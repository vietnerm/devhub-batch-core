package com.devhub.batch.api.service;

import com.devhub.batch.api.security.AuthService.BadCredentialsException;
import com.devhub.batch.core.repository.JdbcTaskRepository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

/** Consistent JSON error shape: {status, message, timestamp}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public Map<String, Object> apiException(ApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Map<String, Object> badCredentials(BadCredentialsException e) {
        return error(401, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Map<String, Object> accessDenied(AccessDeniedException e) {
        return error(403, "Yêu cầu vai trò ADMIN (ADMIN role required)");
    }

    @ExceptionHandler({IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,
            HttpRequestMethodNotSupportedException.class})
    public Map<String, Object> badRequest(Exception e) {
        return error(400, e.getMessage() == null ? "Invalid request" : e.getMessage());
    }

    @ExceptionHandler(RepositoryException.class)
    public Map<String, Object> repository(RepositoryException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("unique") || message.contains("duplicate") || message.contains("uk_task_name")) {
            return error(409, "TASK_NAME đã tồn tại (task name must be unique)");
        }
        log.error("event=api.repository_error", e);
        return error(500, "Database operation failed: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> unexpected(Exception e) {
        log.error("event=api.unexpected_error", e);
        return error(500, "Internal error: " + e.getMessage());
    }

    private static Map<String, Object> error(int status, String message) {
        return Map.of(
                "status", status,
                "message", message == null ? "" : message,
                "timestamp", Instant.now().toString());
    }
}
