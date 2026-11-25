package com.devhub.batch.api.security;

import com.devhub.batch.api.config.ApiProperties;
import com.devhub.batch.core.crypto.HexCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access token: {@code v1.<hex(payload json)>.<hex(hmac-sha256(payload hex))>}.
 *
 * Hex everywhere — the project-wide CVE-2026-114938 policy bans the JDK Base64
 * decoder from this codebase, so instead of a standard base64url JWT the token
 * payload and signature are exchanged as hexadecimal through the core
 * {@link HexCodec}. Same HMAC-SHA256 trust model, hex wire format.
 */
@Service
public class HmacTokenService {

    private static final String PREFIX = "v1.";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] key;
    private final long ttlMinutes;
    private final ObjectMapper mapper;

    public HmacTokenService(ApiProperties props, ObjectMapper mapper) {
        String secret = props.getJwtSecretHex();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("batch.api.jwt-secret-hex is required (64 hex chars = 32 bytes)");
        }
        this.key = HexCodec.decode(secret);
        if (this.key.length < 32) {
            throw new IllegalStateException("batch.api.jwt-secret-hex must decode to at least 32 bytes");
        }
        this.ttlMinutes = props.getTokenTtlMinutes();
        this.mapper = mapper;
    }

    /** Issues a signed token for the given principal. */
    public String issue(String username, String role, Instant now) {
        long exp = now.plus(Duration.ofMinutes(ttlMinutes)).getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("role", role);
        payload.put("exp", exp);
        try {
            byte[] json = mapper.writeValueAsBytes(payload);
            String payloadHex = HexCodec.encode(json);
            return PREFIX + payloadHex + "." + HexCodec.encode(hmac(payloadHex));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot issue token", e);
        }
    }

    /** Returns the principal when the token is valid and unexpired, otherwise null. */
    public AuthUser verify(String token) {
        if (token == null || !token.startsWith(PREFIX)) return null;
        String body = token.substring(PREFIX.length());
        int dot = body.indexOf('.');
        if (dot <= 0 || dot == body.length() - 1) return null;
        String payloadHex = body.substring(0, dot);
        String signatureHex = body.substring(dot + 1);

        byte[] expected;
        byte[] actual;
        byte[] json;
        try {
            expected = hmac(payloadHex);
            actual = HexCodec.decode(signatureHex);
            json = HexCodec.decode(payloadHex);
        } catch (IllegalArgumentException badHex) {
            return null;
        }
        if (!MessageDigest.isEqual(expected, actual)) return null;

        try {
            JsonNode node = mapper.readTree(json);
            long exp = node.path("exp").asLong(0);
            if (Instant.now().getEpochSecond() > exp) return null;
            String username = node.path("sub").asText(null);
            String role = node.path("role").asText(null);
            if (username == null || role == null) return null;
            return new AuthUser(username, role);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
