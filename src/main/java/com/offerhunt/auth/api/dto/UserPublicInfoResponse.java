package com.offerhunt.auth.api.dto;

import java.util.UUID;

public record UserPublicInfoResponse(
        UUID userId,
        String fullName
) { }
