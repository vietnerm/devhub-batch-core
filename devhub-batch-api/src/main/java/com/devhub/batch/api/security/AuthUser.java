package com.devhub.batch.api.security;

/** Authenticated principal carried in the security context. */
public record AuthUser(String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
