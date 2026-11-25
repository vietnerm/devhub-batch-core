package com.devhub.batch.api.dto;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(String token, String username, String role, String expiresAt) {}
}
