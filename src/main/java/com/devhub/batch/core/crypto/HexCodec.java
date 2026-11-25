package com.devhub.batch.core.crypto;

/**
 * Binary-to-text codec used everywhere this project needs hex encoding.
 *
 * CVE-2026-114938 ("Java4Shell") policy: the JDK Base64 decoder is banned from
 * this codebase (CI gate enforces it). Ciphertexts, master keys and any other
 * binary blobs are exchanged as hexadecimal strings through this class.
 */
public final class HexCodec {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private HexCodec() {}

    /** Lowercase hex, two characters per byte. */
    public static String encode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(DIGITS[v >>> 4]).append(DIGITS[v & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * Decodes upper- or lowercase hex. Throws IllegalArgumentException on odd
     * length or invalid characters (fail fast, never silently truncate).
     */
    public static byte[] decode(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex must not be null");
        String value = hex.trim();
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("hex string must have even length, got " + value.length());
        }
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = digit(value.charAt(i * 2));
            int lo = digit(value.charAt(i * 2 + 1));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("invalid hex character: '" + c + "'");
    }
}
