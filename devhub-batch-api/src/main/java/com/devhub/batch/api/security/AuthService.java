package com.devhub.batch.api.security;

import com.devhub.batch.api.config.ApiProperties;
import com.devhub.batch.api.dto.AuthDtos.LoginResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Credential store from configuration (batch.api.users). Plaintext demo
 * passwords are BCrypt-encoded at startup; production deployments should use
 * password-hash entries or inject them via env vars.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ApiProperties props;
    private final PasswordEncoder encoder;
    private final HmacTokenService tokens;
    private final long ttlMinutes;

    private final Map<String, StoredUser> store = new HashMap<>();

    private record StoredUser(String username, String passwordHash, String role) {}

    public AuthService(ApiProperties props,
                       PasswordEncoder encoder,
                       HmacTokenService tokens,
                       @Value("${batch.api.token-ttl-minutes:480}") long ttlMinutes) {
        this.props = props;
        this.encoder = encoder;
        this.tokens = tokens;
        this.ttlMinutes = ttlMinutes;
    }

    @PostConstruct
    void loadUsers() {
        for (ApiProperties.User u : props.getUsers()) {
            if (u.getUsername() == null || u.getUsername().isBlank()) continue;
            String hash = u.getPasswordHash() != null && !u.getPasswordHash().isBlank()
                    ? u.getPasswordHash()
                    : encoder.encode(u.getPassword() == null ? "" : u.getPassword());
            store.put(u.getUsername(), new StoredUser(u.getUsername(), hash, normalizeRole(u.getRole())));
        }
        log.info("event=auth.users_loaded count={} roles={}",
                store.size(), store.values().stream().map(StoredUser::role).toList());
    }

    public LoginResponse login(String username, String rawPassword) {
        StoredUser user = username == null ? null : store.get(username);
        if (user == null || !encoder.matches(rawPassword == null ? "" : rawPassword, user.passwordHash())) {
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu (invalid credentials)");
        }
        String token = tokens.issue(user.username(), user.role(), Instant.now());
        Instant expiresAt = Instant.now().plusSeconds(ttlMinutes * 60);
        return new LoginResponse(token, user.username(), user.role(), expiresAt.toString());
    }

    private static String normalizeRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "VIEWER";
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message) { super(message); }
    }
}
