package com.devhub.batch.api.controller;

import com.devhub.batch.api.dto.AuthDtos.LoginRequest;
import com.devhub.batch.api.dto.AuthDtos.LoginResponse;
import com.devhub.batch.api.security.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
}
