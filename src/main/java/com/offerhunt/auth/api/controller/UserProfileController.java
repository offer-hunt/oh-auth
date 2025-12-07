package com.offerhunt.auth.api.controller;

import com.offerhunt.auth.api.dto.UserProfileResponse;
import com.offerhunt.auth.api.dto.UserPublicInfoResponse;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserRepo userRepo;

    public UserProfileController(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping
    public UserProfileResponse getProfile(JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        UUID userId = UUID.fromString(jwt.getSubject());

        try {
            UserEntity user = userRepo.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("user not found"));

            log.info("event=ProfilePageOpened userId={}", userId);

            return new UserProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getGlobalRole(),
                    user.getCreatedAt(),
                    user.getLastLoginAt(),
                    user.getEmailVerifiedAt(),
                    jwt.getIssuer() != null ? jwt.getIssuer().toString() : null,
                    jwt.getAudience()
            );
        } catch (DataAccessException ex) {
            log.error("event=ProfilePageLoadFailed reason=db_error userId={}", userId, ex);
            throw ex;
        }
    }

    @GetMapping("/{id}")
    public UserPublicInfoResponse getUserPublicInfo(@PathVariable("id") UUID userId) {
        try {
            UserEntity user = userRepo.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("user not found"));

            return new UserPublicInfoResponse(
                    user.getId(),
                    user.getFullName()
            );
        } catch (DataAccessException ex) {
            log.error("event=UserLookupFailed reason=db_error userId={}", userId, ex);
            throw ex;
        }
    }
}
