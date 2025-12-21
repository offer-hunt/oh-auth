package com.offerhunt.auth.api.dto;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword,
    String newPasswordConfirmation
) { }
