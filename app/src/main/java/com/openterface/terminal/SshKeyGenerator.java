package com.openterface.terminal;

import android.util.Log;

import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;

/**
 * SSH key pair generator using Apache MINA SSHD library.
 * <p>
 * Generates keys in OpenSSH-compatible format, ensuring full compatibility
 * with OpenSSH, GitHub, GitLab, and other standard SSH tools.
 * <p>
 * Supported key types:
 * <ul>
 *   <li>Ed25519 - modern, secure, recommended</li>
 *   <li>RSA (2048/4096 bits) - widely compatible</li>
 * </ul>
 * <p>
 * Requires:
 * <ul>
 *   <li>{@code org.apache.sshd:sshd-common:2.14.0}</li>
 *   <li>{@code net.i2p.crypto:eddsa:0.3.0} (for Ed25519 on Java 8 / Android 8)</li>
 * </ul>
 *
 * @see <a href="https://github.com/apache/mina-sshd">Apache MINA SSHD</a>
 */
public class SshKeyGenerator {
    private static final String TAG = "SshKeyGenerator";

    /**
     * Algorithm name for Ed25519/EdDSA key generation.
     */
    private static final String ED25519_ALGORITHM = "EdDSA";

    /**
     * Result of key pair generation containing both private and public keys.
     */
    public static class KeyPairResult {
        /** PEM format private key (OpenSSH format) */
        public final String privateKey;
        /** OpenSSH format public key (e.g., "ssh-ed25519 AAAA... comment") */
        public final String publicKey;

        public KeyPairResult(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Ed25519 Key Generation
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Generate an Ed25519 key pair without passphrase or comment.
     */
    public static KeyPairResult generateEd25519(String comment) throws Exception {
        return generateEd25519(comment, null);
    }

    /**
     * Generate an Ed25519 key pair with optional passphrase encryption (default 16 rounds).
     *
     * @param comment    key comment (e.g., user@host)
     * @param passphrase optional passphrase for encryption (null or empty = no encryption)
     */
    public static KeyPairResult generateEd25519(String comment, String passphrase) throws Exception {
        Log.v(TAG, "Generating Ed25519 key pair using Apache MINA SSHD...");
        return generateEd25519(comment, passphrase, 16);
    }

    /**
     * Generate an Ed25519 key pair with optional passphrase encryption.
     * <p>
     * Uses Apache MINA SSHD's {@link OpenSSHKeyPairResourceWriter} to produce
     * keys that are fully compatible with OpenSSH, GitHub, GitLab, and other
     * standard SSH tools.
     *
     * @param comment    key comment (e.g., user@host)
     * @param passphrase optional passphrase for encryption (null or empty = no encryption)
     * @param rounds     bcrypt-pbkdf iteration count (minimum 16; only used when passphrase is set)
     * @throws Exception if key generation fails
     */
    public static KeyPairResult generateEd25519(String comment, String passphrase, int rounds) throws Exception {
        Log.v(TAG, "Generating Ed25519 key pair using Apache MINA SSHD...");

        // Clamp rounds to safe range [16, 1024] — MINA SSHD enforces minimum 16
        if (rounds < 16) rounds = 16;
        if (rounds > 1024) rounds = 1024;

        // Step 1: Generate Ed25519 key pair via MINA SSHD's SecurityUtils
        java.security.KeyPairGenerator keyGen = SecurityUtils.getKeyPairGenerator(ED25519_ALGORITHM);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Step 2: Write keys in OpenSSH format using MINA SSHD
        OpenSSHKeyPairResourceWriter writer = OpenSSHKeyPairResourceWriter.INSTANCE;

        // Write private key (PEM format, optionally encrypted)
        String pubKeyComment = (comment != null && !comment.isEmpty()) ? comment : "key";
        ByteArrayOutputStream privateKeyStream = new ByteArrayOutputStream();

        if (passphrase != null && !passphrase.isEmpty()) {
            Log.v(TAG, "Encrypting Ed25519 private key (rounds=" + rounds + ")...");
            OpenSSHKeyEncryptionContext encCtx = new OpenSSHKeyEncryptionContext();
            encCtx.setPassword(passphrase);
            encCtx.setKdfRounds(rounds);
            encCtx.setCipherType("256"); // AES-256-CTR (default mode is CTR)
            writer.writePrivateKey(keyPair, pubKeyComment, encCtx, privateKeyStream);
        } else {
            writer.writePrivateKey(keyPair, pubKeyComment, null, privateKeyStream);
        }

        String privateKey = privateKeyStream.toString("UTF-8");

        // Write public key (OpenSSH format: "ssh-ed25519 AAAA... comment")
        ByteArrayOutputStream publicKeyStream = new ByteArrayOutputStream();
        writer.writePublicKey(keyPair.getPublic(), pubKeyComment, publicKeyStream);
        String publicKey = publicKeyStream.toString("UTF-8").trim();

        Log.v(TAG, "Generated Ed25519 key pair successfully");
        return new KeyPairResult(privateKey, publicKey);
    }

    // ════════════════════════════════════════════════════════════════════════
    // RSA Key Generation
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Generate an RSA key pair without passphrase or comment.
     */
    public static KeyPairResult generateRSA(int bits) throws Exception {
        return generateRSA(bits, null, null);
    }

    /**
     * Generate an RSA key pair with comment but no passphrase.
     */
    public static KeyPairResult generateRSA(int bits, String comment) throws Exception {
        return generateRSA(bits, comment, null);
    }

    /**
     * Generate an RSA key pair with optional passphrase encryption.
     * <p>
     * Uses Apache MINA SSHD's {@link OpenSSHKeyPairResourceWriter} to produce
     * keys in OpenSSH format, fully compatible with standard SSH tools.
     *
     * @param bits       key size in bits (2048 or 4096 recommended)
     * @param comment    key comment (e.g., user@host)
     * @param passphrase optional passphrase for encryption (null or empty = no encryption)
     * @throws Exception if key generation fails
     */
    public static KeyPairResult generateRSA(int bits, String comment, String passphrase) throws Exception {
        Log.v(TAG, "Generating RSA-" + bits + " key pair using Apache MINA SSHD...");

        // Step 1: Generate RSA key pair
        java.security.KeyPairGenerator keyGen = SecurityUtils.getKeyPairGenerator("RSA");
        keyGen.initialize(bits);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Step 2: Write keys in OpenSSH format using MINA SSHD
        OpenSSHKeyPairResourceWriter writer = OpenSSHKeyPairResourceWriter.INSTANCE;

        String pubKeyComment = (comment != null && !comment.isEmpty()) ? comment : "key";

        // Write private key
        ByteArrayOutputStream privateKeyStream = new ByteArrayOutputStream();
        if (passphrase != null && !passphrase.isEmpty()) {
            Log.v(TAG, "Encrypting RSA private key...");
            OpenSSHKeyEncryptionContext encCtx = new OpenSSHKeyEncryptionContext();
            encCtx.setPassword(passphrase);
            encCtx.setKdfRounds(16);
            encCtx.setCipherType("256"); // AES-256-CTR
            writer.writePrivateKey(keyPair, pubKeyComment, encCtx, privateKeyStream);
        } else {
            writer.writePrivateKey(keyPair, pubKeyComment, null, privateKeyStream);
        }
        String privateKey = privateKeyStream.toString("UTF-8");

        // Write public key (OpenSSH format: "ssh-rsa AAAA... comment")
        ByteArrayOutputStream publicKeyStream = new ByteArrayOutputStream();
        writer.writePublicKey(keyPair.getPublic(), pubKeyComment, publicKeyStream);
        String publicKey = publicKeyStream.toString("UTF-8").trim();

        Log.v(TAG, "Generated RSA-" + bits + " key pair successfully");
        return new KeyPairResult(privateKey, publicKey);
    }
}
