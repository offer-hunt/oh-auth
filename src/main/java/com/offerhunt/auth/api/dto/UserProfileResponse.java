package com.offerhunt.auth.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        Instant createdAt,
        Instant lastLoginAt,
        Instant emailVerifiedAt,
        String issuer,
        List<String> audience
) { }
