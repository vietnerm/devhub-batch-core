package com.devhub.batch.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AES-256-GCM authenticated encryption for configuration secrets
 * (for example the database password stored as {@code enc:<hex>}).
 *
 * Wire format: hex(IV[12] || ciphertext+tag). The only binary-to-text layer is
 * {@link HexCodec}; the banned JDK Base64 decoder is never used.
 */
public final class AesGcm {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private AesGcm() {}

    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(key), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(requirePlain(plaintext));
            byte[] out = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, out, IV_LENGTH, cipherText.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public static byte[] decrypt(byte[] blob, byte[] key) {
        try {
            if (blob == null || blob.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[blob.length - IV_LENGTH];
            System.arraycopy(blob, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(key), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed (wrong key or tampered data)", e);
        }
    }

    /** Hex convenience wrappers used by config/CLI. */
    public static String encryptToHex(String plaintext, byte[] key) {
        return HexCodec.encode(encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8), key));
    }

    public static String decryptFromHex(String hex, byte[] key) {
        return new String(decrypt(HexCodec.decode(hex), key), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Master key must be exactly 32 bytes (AES-256), passed as 64 hex characters. */
    public static byte[] keyFromHex(String hex) {
        byte[] key = HexCodec.decode(hex);
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("master key must be " + KEY_BYTES + " bytes (64 hex chars), got " + key.length);
        }
        return key;
    }

    private static SecretKey key(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 requires a 32-byte key");
        }
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] requirePlain(byte[] plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext must not be null");
        return plaintext;
    }
}
