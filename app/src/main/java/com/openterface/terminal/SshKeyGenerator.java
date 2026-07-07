package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SSH key pair generator using JSch and BouncyCastle.
 * - RSA: Generated and encrypted via JSch (supports passphrase)
 * - Ed25519: Generated via BouncyCastle, encrypted via manual OpenSSH format
 */
public class SshKeyGenerator {
    private static final String TAG = "SshKeyGenerator";

    /**
     * Result of key pair generation containing both private and public keys.
     */
    public static class KeyPairResult {
        public final String privateKey;  // PEM format private key
        public final String publicKey;   // OpenSSH format public key

        public KeyPairResult(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    // ─── RSA ────────────────────────────────────────────────────────────

    public static KeyPairResult generateRSA(int bits) throws Exception {
        return generateRSA(bits, null, null);
    }

    public static KeyPairResult generateRSA(int bits, String comment) throws Exception {
        return generateRSA(bits, comment, null);
    }

    /**
     * Generate RSA key pair with optional passphrase encryption via JSch.
     */
    public static KeyPairResult generateRSA(int bits, String comment, String passphrase) throws Exception {
        Log.d(TAG, "Generating RSA-" + bits + " key pair...");
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, bits);

        ByteArrayOutputStream privateKeyStream = new ByteArrayOutputStream();
        if (passphrase != null && !passphrase.isEmpty()) {
            keyPair.writePrivateKey(privateKeyStream, passphrase.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            keyPair.writePrivateKey(privateKeyStream);
        }
        String privateKey = privateKeyStream.toString("UTF-8");

        String pubKeyComment = comment != null ? comment : "key";
        ByteArrayOutputStream publicKeyStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyStream, pubKeyComment);
        String publicKey = publicKeyStream.toString("UTF-8").trim();

        keyPair.dispose();

        Log.d(TAG, "Generated RSA " + bits + " key pair successfully");
        return new KeyPairResult(privateKey, publicKey);
    }

    // ─── Ed25519 ────────────────────────────────────────────────────────

    public static KeyPairResult generateEd25519(String comment) throws Exception {
        return generateEd25519(comment, null);
    }

    public static KeyPairResult generateEd25519(String comment, String passphrase) throws Exception {
        return generateEd25519(comment, passphrase, 16);
    }

    /**
     * Generate Ed25519 key pair with optional passphrase encryption.
     * Uses BouncyCastle for key generation and encryption.
     *
     * @param comment    key comment (e.g. user@host)
     * @param passphrase optional passphrase for encryption (null or empty = no encryption)
     * @param rounds     bcrypt-pbkdf iteration count (only used when passphrase is set; default 16)
     */
    public static KeyPairResult generateEd25519(String comment, String passphrase, int rounds) throws Exception {
        Log.d(TAG, "Generating Ed25519 key pair using BouncyCastle...");

        // Clamp rounds to safe range [1, 1024] to prevent DoS from extreme values
        if (rounds < 1) rounds = 16;
        if (rounds > 1024) rounds = 1024;

        // Step 1: Generate Ed25519 key pair with BouncyCastle
        Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
        keyGen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

        Ed25519PrivateKeyParameters privateKeyParams = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters publicKeyParams = (Ed25519PublicKeyParameters) keyPair.getPublic();

        byte[] privateKeyBytes = privateKeyParams.getEncoded();
        byte[] publicKeyBytes = publicKeyParams.getEncoded();

        // Step 2: Generate public key string
        String pubKeyComment = comment != null && !comment.isEmpty() ? comment : "key";
        String publicKey = "ssh-ed25519 " +
            android.util.Base64.encodeToString(encodeEd25519PublicKey(publicKeyBytes),
                android.util.Base64.NO_WRAP) + " " + pubKeyComment;

        // Step 3: Generate private key PEM (encrypted or unencrypted)
        String privateKeyPem;
        if (passphrase != null && !passphrase.isEmpty()) {
            Log.d(TAG, "Encrypting Ed25519 private key with passphrase (rounds=" + rounds + ")...");
            privateKeyPem = generateEd25519PrivateKeyPemEncrypted(privateKeyBytes, publicKeyBytes, passphrase, rounds);
            Log.d(TAG, "Generated encrypted Ed25519 key pair successfully");
        } else {
            privateKeyPem = generateEd25519PrivateKeyPemUnencrypted(privateKeyBytes, publicKeyBytes);
            Log.d(TAG, "Generated Ed25519 key pair successfully");
        }

        return new KeyPairResult(privateKeyPem, publicKey);
    }

    // ─── Ed25519 PEM encoding helpers ───────────────────────────────────

    private static byte[] encodeEd25519PublicKey(byte[] publicKeyBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] keyType = "ssh-ed25519".getBytes();
        writeLength(out, keyType.length);
        out.write(keyType, 0, keyType.length);
        writeLength(out, publicKeyBytes.length);
        out.write(publicKeyBytes, 0, publicKeyBytes.length);
        return out.toByteArray();
    }

    /**
     * Generate Ed25519 private key in unencrypted OpenSSH PEM format.
     */
    private static String generateEd25519PrivateKeyPemUnencrypted(byte[] privateKeyBytes, byte[] publicKeyBytes)
            throws Exception {
        return generateEd25519PrivateKeyPem(privateKeyBytes, publicKeyBytes, null, 0);
    }

    /**
     * Generate Ed25519 private key in encrypted OpenSSH PEM format using bcrypt_pbkdf + AES-256-CTR.
     */
    private static String generateEd25519PrivateKeyPemEncrypted(byte[] privateKeyBytes, byte[] publicKeyBytes,
                                                                  String passphrase, int rounds)
            throws Exception {
        return generateEd25519PrivateKeyPem(privateKeyBytes, publicKeyBytes, passphrase, rounds);
    }

    /**
     * Generate Ed25519 private key in OpenSSH PEM format, optionally encrypted.
     */
    private static String generateEd25519PrivateKeyPem(byte[] privateKeyBytes, byte[] publicKeyBytes,
                                                        String passphrase, int rounds)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Auth magic
        out.write("openssh-key-v1\0".getBytes());

        boolean encrypted = passphrase != null && !passphrase.isEmpty() && rounds > 0;

        // Cipher and KDF names
        String cipherName = encrypted ? "aes256-ctr" : "none";
        String kdfName = encrypted ? "bcrypt" : "none";

        byte[] cipherNameBytes = cipherName.getBytes();
        writeLength(out, cipherNameBytes.length);
        out.write(cipherNameBytes, 0, cipherNameBytes.length);

        byte[] kdfNameBytes = kdfName.getBytes();
        writeLength(out, kdfNameBytes.length);
        out.write(kdfNameBytes, 0, kdfNameBytes.length);

        // KDF options
        if (encrypted) {
            // Generate salt (16 bytes)
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            // Build KDF options: {salt, rounds}
            ByteArrayOutputStream kdfOptions = new ByteArrayOutputStream();
            writeLength(kdfOptions, salt.length);
            kdfOptions.write(salt);
            writeLength(kdfOptions, rounds);

            byte[] kdfOptionsBytes = kdfOptions.toByteArray();
            writeLength(out, kdfOptionsBytes.length);
            out.write(kdfOptionsBytes, 0, kdfOptionsBytes.length);

            // Derive key material using OpenSSH-compatible bcrypt_pbkdf
            // Need 48 bytes: 32 for AES key + 16 for IV (AES block size)
            byte[] keyMaterial = OpenSSHBcryptPbkdf.bcrypt_pbkdf(
                    passphrase.getBytes(java.nio.charset.StandardCharsets.UTF_8), salt, rounds, 48);
            byte[] aesKey = Arrays.copyOfRange(keyMaterial, 0, 32);
            byte[] iv = Arrays.copyOfRange(keyMaterial, 32, 48);

            // Build unencrypted private key section
            byte[] unencryptedSection = buildPrivateKeySection(privateKeyBytes, publicKeyBytes);

            // Encrypt with AES-256-CTR
            byte[] encryptedSection = aes256CtrEncrypt(unencryptedSection, aesKey, iv);

            // Zero out sensitive key material from memory
            Arrays.fill(keyMaterial, (byte) 0);
            Arrays.fill(aesKey, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(unencryptedSection, (byte) 0);

            // Number of keys
            writeLength(out, 1);

            // Public key
            byte[] publicKeyEncoded = encodeEd25519PublicKey(publicKeyBytes);
            writeLength(out, publicKeyEncoded.length);
            out.write(publicKeyEncoded, 0, publicKeyEncoded.length);

            // Encrypted private key section
            writeLength(out, encryptedSection.length);
            out.write(encryptedSection, 0, encryptedSection.length);
        } else {
            // KDF options (empty)
            writeLength(out, 0);

            // Number of keys
            writeLength(out, 1);

            // Public key
            byte[] publicKeyEncoded = encodeEd25519PublicKey(publicKeyBytes);
            writeLength(out, publicKeyEncoded.length);
            out.write(publicKeyEncoded, 0, publicKeyEncoded.length);

            // Unencrypted private key section
            byte[] section = buildPrivateKeySection(privateKeyBytes, publicKeyBytes);
            writeLength(out, section.length);
            out.write(section, 0, section.length);
        }

        // Encode to Base64 and format as PEM
        String base64 = android.util.Base64.encodeToString(out.toByteArray(),
            android.util.Base64.NO_WRAP);

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN OPENSSH PRIVATE KEY-----\n");
        int index = 0;
        while (index < base64.length()) {
            int end = Math.min(index + 70, base64.length());
            pem.append(base64, index, end).append("\n");
            index = end;
        }
        pem.append("-----END OPENSSH PRIVATE KEY-----\n");

        return pem.toString();
    }

    /**
     * Build the private key section (unencrypted).
     */
    private static byte[] buildPrivateKeySection(byte[] privateKeyBytes, byte[] publicKeyBytes)
            throws java.io.IOException {
        ByteArrayOutputStream section = new ByteArrayOutputStream();

        // Check integers (random)
        int checkInt = new SecureRandom().nextInt();
        byte[] checkBytes = new byte[4];
        checkBytes[0] = (byte) (checkInt >> 24);
        checkBytes[1] = (byte) (checkInt >> 16);
        checkBytes[2] = (byte) (checkInt >> 8);
        checkBytes[3] = (byte) checkInt;
        section.write(checkBytes, 0, 4);
        section.write(checkBytes, 0, 4);

        // Key type
        byte[] keyType = "ssh-ed25519".getBytes();
        writeLength(section, keyType.length);
        section.write(keyType, 0, keyType.length);

        // Public key
        writeLength(section, publicKeyBytes.length);
        section.write(publicKeyBytes, 0, publicKeyBytes.length);

        // Private key (with length prefix)
        byte[] privateKeyWithLength = new byte[privateKeyBytes.length + 4];
        writeLengthTo(privateKeyWithLength, 0, privateKeyBytes.length);
        System.arraycopy(privateKeyBytes, 0, privateKeyWithLength, 4, privateKeyBytes.length);
        writeLength(section, privateKeyWithLength.length);
        section.write(privateKeyWithLength, 0, privateKeyWithLength.length);

        // Comment (empty)
        writeLength(section, 0);

        // Padding
        byte[] sectionBytes = section.toByteArray();
        int padding = 8 - (sectionBytes.length % 8);
        if (padding < 8) {
            byte[] paddingBytes = new byte[padding];
            for (int i = 0; i < padding; i++) {
                paddingBytes[i] = (byte) (i + 1);
            }
            section.write(paddingBytes, 0, padding);
        }

        return section.toByteArray();
    }

    /**
     * Encrypt data using AES-256-CTR.
     */
    private static byte[] aes256CtrEncrypt(byte[] data, byte[] key, byte[] iv) {
        AESEngine engine = new AESEngine();
        SICBlockCipher ctrCipher = new SICBlockCipher(engine);
        ctrCipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));

        byte[] output = new byte[data.length];
        ctrCipher.processBytes(data, 0, data.length, output, 0);
        return output;
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        out.write((length >> 24) & 0xFF);
        out.write((length >> 16) & 0xFF);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
    }

    private static void writeLengthTo(byte[] arr, int offset, int length) {
        arr[offset] = (byte) (length >> 24);
        arr[offset + 1] = (byte) (length >> 16);
        arr[offset + 2] = (byte) (length >> 8);
        arr[offset + 3] = (byte) length;
    }
}
