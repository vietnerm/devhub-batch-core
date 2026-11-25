package com.devhub.batch.api.service;

import com.devhub.batch.api.security.AuthService.BadCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void invalidCredentialsReturnUnauthorizedHttpStatus() {
        var response = new GlobalExceptionHandler()
                .badCredentials(new BadCredentialsException("invalid credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
    }
}
