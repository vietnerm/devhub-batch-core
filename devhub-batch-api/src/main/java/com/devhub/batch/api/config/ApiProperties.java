package com.devhub.batch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * API security settings. {@code jwt-secret-hex} must be 64 hex characters
 * (32 bytes) — hex only, per the project-wide no-Base64 policy.
 */
@ConfigurationProperties(prefix = "batch.api")
public class ApiProperties {

    private String jwtSecretHex;
    private long tokenTtlMinutes = 480;
    private List<User> users = new ArrayList<>();

    public String getJwtSecretHex() { return jwtSecretHex; }
    public void setJwtSecretHex(String v) { this.jwtSecretHex = v; }

    public long getTokenTtlMinutes() { return tokenTtlMinutes; }
    public void setTokenTtlMinutes(long v) { this.tokenTtlMinutes = v; }

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> v) { this.users = v; }

    public static class User {
        private String username;
        private String password;
        private String passwordHash;
        private String role = "VIEWER";

        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String v) { this.passwordHash = v; }
        public String getRole() { return role; }
        public void setRole(String v) { this.role = v; }
    }
}
