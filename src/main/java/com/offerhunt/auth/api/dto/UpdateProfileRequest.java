package com.offerhunt.auth.api.dto;

public record UpdateProfileRequest(
    String fullName,
    String bio
) { }
