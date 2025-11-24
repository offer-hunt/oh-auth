package com.offerhunt.auth.api.dto;

public record PasswordResetRequest(
    String token,
    String password,
    String passwordConfirmation
) {
}
