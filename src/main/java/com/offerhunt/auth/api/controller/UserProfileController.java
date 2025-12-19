package com.offerhunt.auth.api.controller;

import com.offerhunt.auth.api.dto.UpdateProfileRequest;
import com.offerhunt.auth.api.dto.UserProfileResponse;
import com.offerhunt.auth.api.dto.UserPublicInfoResponse;
import com.offerhunt.auth.domain.model.UserEntity;
import com.offerhunt.auth.domain.service.UserProfileService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserProfileResponse getProfile(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        UserEntity user = profileService.getUserRequired(userId);

        log.info("event=ProfilePageOpened userId={}", userId);

        return toProfileResponse(user, auth);
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());

        String name = req != null ? req.fullName() : null;
        if (name == null || name.trim().isBlank()) {
            log.info("Profile update failed - empty name userId={}", userId);
            return ResponseEntity.badRequest().body(Map.of("message", "Имя не может быть пустым"));
        }

        try {
            UserEntity saved = profileService.updateProfile(userId, req.fullName(), req.bio());
            return ResponseEntity.ok(Map.of(
                "message", "Изменения сохранены",
                "profile", toProfileResponse(saved, auth)
            ));
        } catch (UserProfileService.NameTooLongException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Имя не может быть длиннее 50 символов"));
        } catch (UserProfileService.BioTooLongException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Описание не может быть длиннее 500 символов"));
        }
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(
        @RequestParam("file") MultipartFile file,
        JwtAuthenticationToken auth
    ) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());

        try {
            UserEntity saved = profileService.uploadAvatar(userId, file);
            return ResponseEntity.ok(Map.of(
                "message", "Аватар обновлен",
                "avatarUrl", buildAvatarUrl(saved),
                "avatarUpdatedAt", saved.getAvatarUpdatedAt()
            ));
        } catch (UserProfileService.InvalidAvatarFormatException ex) {
            log.info("Avatar upload failed - invalid format userId={}", userId);
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Неверный формат файла. Поддерживаются JPG, PNG, WEBP"
            ));
        } catch (UserProfileService.AvatarTooLargeException ex) {
            log.info("Avatar upload failed - file too large userId={}", userId);
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Размер файла не должен превышать 5 МБ"
            ));
        }
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<?> deleteAvatar(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        profileService.deleteAvatar(userId);
        return ResponseEntity.ok(Map.of("message", "Аватар удален"));
    }

    @GetMapping("/avatar")
    public ResponseEntity<Resource> getAvatar(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());

        var loaded = profileService.loadAvatar(userId);
        if (loaded == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (loaded.contentType() != null) {
            try {
                mt = MediaType.parseMediaType(loaded.contentType());
            } catch (Exception ignored) {
                // fallback
            }
        }

        return ResponseEntity.ok()
            .contentType(mt)
            .contentLength(Math.max(0L, loaded.sizeBytes()))
            .body(loaded.resource());
    }

    // оставил публичный эндпоинт, но сделал regex чтобы не конфликтовать с /avatar
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public UserPublicInfoResponse getUserPublicInfo(@PathVariable("id") UUID userId) {
        UserEntity user = profileService.getUserRequired(userId);
        return new UserPublicInfoResponse(user.getId(), user.getFullName());
    }

    private UserProfileResponse toProfileResponse(UserEntity user, JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getBio(),
            buildAvatarUrl(user),
            user.getGlobalRole(),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            user.getEmailVerifiedAt(),
            jwt.getIssuer() != null ? jwt.getIssuer().toString() : null,
            jwt.getAudience()
        );
    }

    private String buildAvatarUrl(UserEntity user) {
        if (user.getAvatarKey() == null) return null;
        Instant ts = user.getAvatarUpdatedAt();
        if (ts == null) return "/api/profile/avatar";
        return "/api/profile/avatar?ts=" + ts.toEpochMilli();
    }
}