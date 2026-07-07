package com.openterface.terminal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OpenSSH-compatible bcrypt_pbkdf key derivation function.
 * Reference: OpenSSH openbsd-compat/bcrypt_pbkdf.c
 *
 * Uses Blowfish constants extracted from BouncyCastle's BlowfishEngine
 * to avoid hardcoding 4KB of pi-derived constants.
 */
public class OpenSSHBcryptPbkdf {

    private static final int BCRYPT_HASHSIZE = 32;

    // Blowfish constants extracted from BouncyCastle at class init
    private static final int[] BF_P = new int[18];
    private static final int[] BF_S0 = new int[256];
    private static final int[] BF_S1 = new int[256];
    private static final int[] BF_S2 = new int[256];
    private static final int[] BF_S3 = new int[256];

    static {
        try {
            // Extract Blowfish pi constants from BouncyCastle's BlowfishEngine
            Class<?> bfClass = org.bouncycastle.crypto.engines.BlowfishEngine.class;
            List<int[]> sboxes = new ArrayList<>();

            for (Field f : bfClass.getDeclaredFields()) {
                if (f.getType() == int[].class && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    int[] arr = (int[]) f.get(null);
                    if (arr.length == 18) {
                        System.arraycopy(arr, 0, BF_P, 0, 18);
                    } else if (arr.length == 256) {
                        sboxes.add(arr);
                    }
                }
            }

            if (sboxes.size() >= 4) {
                System.arraycopy(sboxes.get(0), 0, BF_S0, 0, 256);
                System.arraycopy(sboxes.get(1), 0, BF_S1, 0, 256);
                System.arraycopy(sboxes.get(2), 0, BF_S2, 0, 256);
                System.arraycopy(sboxes.get(3), 0, BF_S3, 0, 256);
            } else {
                throw new RuntimeException("Could not extract Blowfish S-boxes from BouncyCastle, found: " + sboxes.size());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract Blowfish constants from BouncyCastle", e);
        }
    }

    // ─── Blowfish State ─────────────────────────────────────────────────

    private static class BlowfishState {
        int[] P = new int[18];
        int[] s0 = new int[256];
        int[] s1 = new int[256];
        int[] s2 = new int[256];
        int[] s3 = new int[256];

        void initState() {
            System.arraycopy(BF_P, 0, P, 0, 18);
            System.arraycopy(BF_S0, 0, s0, 0, 256);
            System.arraycopy(BF_S1, 0, s1, 0, 256);
            System.arraycopy(BF_S2, 0, s2, 0, 256);
            System.arraycopy(BF_S3, 0, s3, 0, 256);
        }

        private int F(int x) {
            return ((s0[(x >> 24) & 0xff] + s1[(x >> 16) & 0xff]) ^ s2[(x >> 8) & 0xff]) + s3[x & 0xff];
        }

        void encipher(int[] lr) {
            int l = lr[0], r = lr[1];
            for (int i = 0; i < 16; i += 2) {
                l ^= P[i];     r ^= F(l);
                r ^= P[i + 1]; l ^= F(r);
            }
            lr[0] = r ^ P[17];
            lr[1] = l ^ P[16];
        }

        /**
         * OpenSSH Blowfish_expandstate: expand state with data and key interleaved.
         * XORs P/S values with data, then encrypts IN PLACE.
         */
        void expandState(byte[] data, byte[] key) {
            int dLen = data.length;
            int kLen = key.length;
            int dOff = 0, kOff = 0;

            // XOR P-array with key bytes
            for (int i = 0; i < 18; i++) {
                int temp = 0;
                for (int k = 0; k < 4; k++) {
                    temp = (temp << 8) | (key[kOff] & 0xff);
                    kOff = (kOff + 1) % kLen;
                }
                P[i] ^= temp;
            }

            // XOR P[i] with data, then encrypt P[i],P[i+1] IN PLACE
            for (int i = 0; i < 18; i += 2) {
                P[i] ^= streamToWord(data, dOff, dLen); dOff = (dOff + 4) % dLen;
                P[i + 1] ^= streamToWord(data, dOff, dLen); dOff = (dOff + 4) % dLen;
                int[] lr = {P[i], P[i + 1]};
                encipher(lr);
                P[i] = lr[0];
                P[i + 1] = lr[1];
            }

            // Data offset continues advancing across all S-box expansions
            dOff = expandSBox(data, dOff, dLen, s0);
            dOff = expandSBox(data, dOff, dLen, s1);
            dOff = expandSBox(data, dOff, dLen, s2);
            dOff = expandSBox(data, dOff, dLen, s3);
        }

        /**
         * Expand S-box with data. Returns updated data offset.
         */
        private int expandSBox(byte[] data, int dOff, int dLen, int[] sbox) {
            for (int i = 0; i < 256; i += 2) {
                sbox[i] ^= streamToWord(data, dOff, dLen); dOff = (dOff + 4) % dLen;
                sbox[i + 1] ^= streamToWord(data, dOff, dLen); dOff = (dOff + 4) % dLen;
                int[] lr = {sbox[i], sbox[i + 1]};
                encipher(lr);
                sbox[i] = lr[0];
                sbox[i + 1] = lr[1];
            }
            return dOff;
        }

        /**
         * OpenSSH Blowfish_expand0state: expand state with key only.
         * XORs P with key, then encrypts P[i],P[i+1] IN PLACE.
         */
        void expand0State(byte[] key) {
            int kLen = key.length;
            int kOff = 0;

            for (int i = 0; i < 18; i++) {
                int temp = 0;
                for (int k = 0; k < 4; k++) {
                    temp = (temp << 8) | (key[kOff] & 0xff);
                    kOff = (kOff + 1) % kLen;
                }
                P[i] ^= temp;
            }

            // Encrypt P[i], P[i+1] IN PLACE
            for (int i = 0; i < 18; i += 2) {
                int[] lr = {P[i], P[i + 1]};
                encipher(lr);
                P[i] = lr[0];
                P[i + 1] = lr[1];
            }
            expandSBox0(s0);
            expandSBox0(s1);
            expandSBox0(s2);
            expandSBox0(s3);
        }

        private void expandSBox0(int[] sbox) {
            for (int i = 0; i < 256; i += 2) {
                int[] lr = {sbox[i], sbox[i + 1]};
                encipher(lr);
                sbox[i] = lr[0];
                sbox[i + 1] = lr[1];
            }
        }

        private int streamToWord(byte[] data, int offset, int len) {
            int word = 0;
            for (int i = 0; i < 4; i++) {
                word = (word << 8) | (data[(offset + i) % len] & 0xff);
            }
            return word;
        }
    }

    // ─── bcrypt_hash (OpenSSH variant, produces 32 bytes) ──────────────

    /**
     * "OxychromaticBlowfishSwatDynamite" as 8 uint32 values.
     * OpenSSH reads bytes as little-endian uint32 on x86, so we match that byte order.
     */
    private static final int[] BCRYPT_CTEXT = {
        0x6379784f, 0x6d6f7268,  // "Oxyc" LE, "hrom" LE
        0x63697461, 0x776f6c42,  // "atic" LE, "Blow" LE
        0x68736966, 0x74617753,  // "fish" LE, "Swat" LE
        0x616e7944, 0x6574696d   // "Dyna" LE, "mite" LE
    };

    private static byte[] bcrypt_hash(byte[] sha2pass, byte[] sha2salt) {
        BlowfishState state = new BlowfishState();
        state.initState();
        state.expandState(sha2salt, sha2pass);

        // OpenSSH bcrypt_hash uses FIXED 64 rounds for inner Blowfish expansion
        for (int i = 0; i < 64; i++) {
            state.expand0State(sha2pass);
            state.expand0State(sha2salt);
        }

        // Copy ciphertext
        int[] ctext = Arrays.copyOf(BCRYPT_CTEXT, BCRYPT_CTEXT.length);

        // Encrypt 64 times
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 8; j += 2) {
                int[] lr = {ctext[j], ctext[j + 1]};
                state.encipher(lr);
                ctext[j] = lr[0];
                ctext[j + 1] = lr[1];
            }
        }

        // Produce 32-byte output in big-endian order
        // (OpenSSH on x86: reads LE uint32 → encrypts → byte-swaps to BE)
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[i * 4]     = (byte) ((ctext[i] >> 24) & 0xff);   // MSB
            out[i * 4 + 1] = (byte) ((ctext[i] >> 16) & 0xff);
            out[i * 4 + 2] = (byte) ((ctext[i] >> 8) & 0xff);
            out[i * 4 + 3] = (byte) (ctext[i] & 0xff);           // LSB
        }
        return out;
    }

    // ─── bcrypt_pbkdf ───────────────────────────────────────────────────

    /**
     * OpenSSH bcrypt_pbkdf key derivation.
     *
     * @param password the password bytes
     * @param salt     the salt bytes
     * @param rounds   number of bcrypt rounds (typically 16)
     * @param keyLen   desired output key length in bytes
     * @return derived key material
     */
    public static byte[] bcrypt_pbkdf(byte[] password, byte[] salt, int rounds, int keyLen)
            throws Exception {
        if (rounds < 1 || keyLen <= 0) {
            throw new IllegalArgumentException("Invalid bcrypt_pbkdf parameters");
        }

        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");

        // Step 1: SHA-512 hash the password
        byte[] sha2pass = sha512.digest(password);

        // Step 2: Generate blocks
        int stride = (keyLen + BCRYPT_HASHSIZE - 1) / BCRYPT_HASHSIZE;
        byte[] keyBuffer = new byte[stride * BCRYPT_HASHSIZE];
        int blocksDone = 0;

        for (int count = 1; blocksDone < stride; count++) {
            // Build countsalt = salt || uint32_be(count)
            byte[] countsalt = new byte[salt.length + 4];
            System.arraycopy(salt, 0, countsalt, 0, salt.length);
            countsalt[salt.length]     = (byte) (count >> 24);
            countsalt[salt.length + 1] = (byte) (count >> 16);
            countsalt[salt.length + 2] = (byte) (count >> 8);
            countsalt[salt.length + 3] = (byte) count;

            // SHA-512 hash the countsalt
            byte[] sha2salt = sha512.digest(countsalt);

            // First bcrypt round
            byte[] block = bcrypt_hash(sha2pass, sha2salt);
            byte[] out = Arrays.copyOf(block, BCRYPT_HASHSIZE);

            // Subsequent rounds
            for (int r = 1; r < rounds; r++) {
                sha2salt = sha512.digest(block);
                block = bcrypt_hash(sha2pass, sha2salt);
                for (int i = 0; i < BCRYPT_HASHSIZE; i++) {
                    out[i] ^= block[i];
                }
            }

            // Store in key buffer
            System.arraycopy(out, 0, keyBuffer, blocksDone * BCRYPT_HASHSIZE, BCRYPT_HASHSIZE);
            blocksDone++;
        }

        // Step 3: Interleave output (stride pattern)
        byte[] result = new byte[keyBuffer.length];
        int pos = 0;
        for (int i = 0; i < BCRYPT_HASHSIZE; i++) {
            for (int j = 0; j < stride; j++) {
                result[pos++] = keyBuffer[j * BCRYPT_HASHSIZE + i];
            }
        }

        return Arrays.copyOf(result, keyLen);
    }
}
