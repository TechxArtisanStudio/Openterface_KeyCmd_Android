package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.security.SecureRandom;

/**
 * SSH key pair generator using JSch and BouncyCastle.
 * Supports RSA key generation for key sizes 2048 and 4096.
 * Supports Ed25519 key generation via BouncyCastle.
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

    /**
     * Generate RSA key pair with specified bit size.
     *
     * @param bits Key size (2048 or 4096 recommended)
     * @return KeyPairResult containing private and public keys
     * @throws Exception if generation fails
     */
    public static KeyPairResult generateRSA(int bits) throws Exception {
        return generateRSA(bits, null);
    }

    /**
     * Generate RSA key pair with specified bit size and comment.
     *
     * @param bits    Key size (2048 or 4096 recommended)
     * @param comment Optional comment for the public key
     * @return KeyPairResult containing private and public keys
     * @throws Exception if generation fails
     */
    public static KeyPairResult generateRSA(int bits, String comment) throws Exception {
        Log.d(TAG, "Generating RSA-" + bits + " key pair...");
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, bits);

        // Export private key in PEM format
        ByteArrayOutputStream privateKeyStream = new ByteArrayOutputStream();
        keyPair.writePrivateKey(privateKeyStream);
        String privateKey = privateKeyStream.toString("UTF-8");

        // Export public key in OpenSSH format
        String pubKeyComment = comment != null ? comment : "key";
        ByteArrayOutputStream publicKeyStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyStream, pubKeyComment);
        String publicKey = publicKeyStream.toString("UTF-8").trim();

        // Clean up
        keyPair.dispose();

        Log.d(TAG, "Generated RSA " + bits + " key pair successfully");
        return new KeyPairResult(privateKey, publicKey);
    }

    /**
     * Generate Ed25519 key pair using BouncyCastle.
     *
     * @param comment Optional comment for the public key
     * @return KeyPairResult containing private and public keys
     * @throws Exception if generation fails
     */
    public static KeyPairResult generateEd25519(String comment) throws Exception {
        Log.d(TAG, "Generating Ed25519 key pair using BouncyCastle...");

        // Generate Ed25519 key pair
        Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
        keyGen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

        Ed25519PrivateKeyParameters privateKeyParams = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters publicKeyParams = (Ed25519PublicKeyParameters) keyPair.getPublic();

        byte[] privateKeyBytes = privateKeyParams.getEncoded();
        byte[] publicKeyBytes = publicKeyParams.getEncoded();

        // Generate private key in OpenSSH PEM format
        String privateKey = generateEd25519PrivateKeyPem(privateKeyBytes, publicKeyBytes);

        // Generate public key in OpenSSH format
        String pubKeyComment = comment != null && !comment.isEmpty() ? comment : "key";
        String publicKey = "ssh-ed25519 " +
            android.util.Base64.encodeToString(encodeEd25519PublicKey(publicKeyBytes),
                android.util.Base64.NO_WRAP) + " " + pubKeyComment;

        Log.d(TAG, "Generated Ed25519 key pair successfully");
        return new KeyPairResult(privateKey, publicKey);
    }

    /**
     * Encode Ed25519 public key in OpenSSH format.
     */
    private static byte[] encodeEd25519PublicKey(byte[] publicKeyBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] keyType = "ssh-ed25519".getBytes();

        // Write key type length and key type
        writeLength(out, keyType.length);
        out.write(keyType, 0, keyType.length);

        // Write public key length and public key
        writeLength(out, publicKeyBytes.length);
        out.write(publicKeyBytes, 0, publicKeyBytes.length);

        return out.toByteArray();
    }

    /**
     * Generate Ed25519 private key in OpenSSH PEM format.
     */
    private static String generateEd25519PrivateKeyPem(byte[] privateKeyBytes, byte[] publicKeyBytes)
            throws java.io.IOException {
        // OpenSSH private key format
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Auth magic
        out.write("openssh-key-v1\0".getBytes());

        // Cipher name (none)
        byte[] cipherName = "none".getBytes();
        writeLength(out, cipherName.length);
        out.write(cipherName, 0, cipherName.length);

        // KDF name (none)
        byte[] kdfName = "none".getBytes();
        writeLength(out, kdfName.length);
        out.write(kdfName, 0, kdfName.length);

        // KDF options (empty)
        writeLength(out, 0);

        // Number of keys
        writeLength(out, 1);

        // Public key
        byte[] publicKeyEncoded = encodeEd25519PublicKey(publicKeyBytes);
        writeLength(out, publicKeyEncoded.length);
        out.write(publicKeyEncoded, 0, publicKeyEncoded.length);

        // Private key section (unencrypted)
        ByteArrayOutputStream privateKeySection = new ByteArrayOutputStream();

        // Check integers (random)
        int checkInt = new SecureRandom().nextInt();
        writeLength(privateKeySection, 4);
        byte[] checkBytes = new byte[4];
        checkBytes[0] = (byte) (checkInt >> 24);
        checkBytes[1] = (byte) (checkInt >> 16);
        checkBytes[2] = (byte) (checkInt >> 8);
        checkBytes[3] = (byte) checkInt;
        privateKeySection.write(checkBytes, 0, 4);
        privateKeySection.write(checkBytes, 0, 4);

        // Key type
        byte[] keyType = "ssh-ed25519".getBytes();
        writeLength(privateKeySection, keyType.length);
        privateKeySection.write(keyType, 0, keyType.length);

        // Public key
        writeLength(privateKeySection, publicKeyBytes.length);
        privateKeySection.write(publicKeyBytes, 0, publicKeyBytes.length);

        // Private key (with length prefix)
        byte[] privateKeyWithLength = new byte[privateKeyBytes.length + 4];
        writeLengthTo(privateKeyWithLength, 0, privateKeyBytes.length);
        System.arraycopy(privateKeyBytes, 0, privateKeyWithLength, 4, privateKeyBytes.length);
        writeLength(privateKeySection, privateKeyWithLength.length);
        privateKeySection.write(privateKeyWithLength, 0, privateKeyWithLength.length);

        // Comment (empty)
        writeLength(privateKeySection, 0);

        // Padding
        byte[] sectionBytes = privateKeySection.toByteArray();
        int padding = 8 - (sectionBytes.length % 8);
        if (padding < 8) {
            byte[] paddingBytes = new byte[padding];
            for (int i = 0; i < padding; i++) {
                paddingBytes[i] = (byte) (i + 1);
            }
            privateKeySection.write(paddingBytes, 0, padding);
        }

        byte[] finalSection = privateKeySection.toByteArray();
        writeLength(out, finalSection.length);
        out.write(finalSection, 0, finalSection.length);

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
