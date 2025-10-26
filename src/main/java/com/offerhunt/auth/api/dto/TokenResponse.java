package com.offerhunt.auth.api.dto;

public record TokenResponse(
    String token_type,
    String access_token,
    long expires_in,
    String refresh_token
) {
}
