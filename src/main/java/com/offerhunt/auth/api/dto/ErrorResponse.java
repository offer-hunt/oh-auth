package com.offerhunt.auth.api.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), null);
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, Instant.now(), details);
    }
}
