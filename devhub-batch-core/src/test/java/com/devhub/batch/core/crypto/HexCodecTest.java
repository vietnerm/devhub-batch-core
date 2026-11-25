package com.devhub.batch.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexCodecTest {

    @Test
    void roundTrip() {
        byte[] input = {0x00, 0x0f, (byte) 0xff, 0x10, (byte) 0xab, (byte) 0xcd};
        String hex = HexCodec.encode(input);
        assertEquals("000fff10abcd", hex);
        assertArrayEquals(input, HexCodec.decode(hex));
    }

    @Test
    void acceptsUppercase() {
        assertArrayEquals(new byte[]{(byte) 0xab}, HexCodec.decode("AB"));
        assertArrayEquals(new byte[]{(byte) 0xab}, HexCodec.decode("aB"));
    }

    @Test
    void rejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> HexCodec.decode("abc"));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> HexCodec.decode("zz"));
        assertThrows(IllegalArgumentException.class, () -> HexCodec.decode("0g"));
    }

    @Test
    void emptyAndNull() {
        assertEquals(0, HexCodec.decode("").length);
        assertThrows(IllegalArgumentException.class, () -> HexCodec.encode(null));
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "byte " + i);
        }
    }

    @Test
    void notTheBannedDecoder() {
        // The project policy bans the JDK Base64 decoder (CVE-2026-114938);
        // HexCodec output must look like plain hex, never like Base64.
        String hex = HexCodec.encode("hex only".getBytes(StandardCharsets.UTF_8));
        assertTrue(hex.matches("[0-9a-f]+"));
        assertNotEquals("", hex);
    }
}
