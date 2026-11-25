package com.devhub.batch.core.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmTest {

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void roundTripHex() {
        byte[] key = randomKey();
        String plaintext = "staging database password";
        String cipherHex = AesGcm.encryptToHex(plaintext, key);

        assertTrue(cipherHex.matches("[0-9a-f]+"));
        assertEquals(plaintext, AesGcm.decryptFromHex(cipherHex, key));
    }

    @Test
    void randomIvMakesCiphertextNonDeterministic() {
        byte[] key = randomKey();
        String a = AesGcm.encryptToHex("same plaintext", key);
        String b = AesGcm.encryptToHex("same plaintext", key);
        assertNotEquals(a, b);
        assertEquals("same plaintext", AesGcm.decryptFromHex(a, key));
        assertEquals("same plaintext", AesGcm.decryptFromHex(b, key));
    }

    @Test
    void wrongKeyFails() {
        String cipherHex = AesGcm.encryptToHex("secret", randomKey());
        assertThrows(IllegalStateException.class, () -> AesGcm.decryptFromHex(cipherHex, randomKey()));
    }

    @Test
    void tamperedCiphertextFails() {
        byte[] key = randomKey();
        byte[] blob = AesGcm.encrypt("secret".getBytes(), key);
        blob[blob.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> AesGcm.decrypt(blob, key));
    }

    @Test
    void keyValidation() {
        assertThrows(IllegalArgumentException.class, () -> AesGcm.keyFromHex("aabbcc"));
        assertEquals(32, AesGcm.keyFromHex(HexCodec.encode(randomKey())).length);
    }

    @Test
    void wireFormatIsIvPrefixedHex() {
        byte[] key = randomKey();
        byte[] blob = AesGcm.encrypt("data".getBytes(), key);
        assertEquals(12 + 4 + 16, blob.length); // IV + plaintext + GCM tag
    }

    @Test
    void hexRoundTripEqualsBytes() {
        byte[] key = randomKey();
        byte[] blob = AesGcm.encrypt("data".getBytes(), key);
        assertArrayEquals(blob, HexCodec.decode(HexCodec.encode(blob)));
    }
}
