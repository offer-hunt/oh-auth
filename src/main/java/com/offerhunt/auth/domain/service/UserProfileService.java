package com.offerhunt.auth.domain.service;

import com.offerhunt.auth.domain.avatar.AvatarStorage;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private static final int FULL_NAME_MAX = 50;
    private static final int BIO_MAX = 500;
    private static final long AVATAR_MAX_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CT = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private final UserRepo userRepo;
    private final AvatarStorage avatarStorage;

    public UserProfileService(UserRepo userRepo, AvatarStorage avatarStorage) {
        this.userRepo = userRepo;
        this.avatarStorage = avatarStorage;
    }

    @Transactional(readOnly = true)
    public UserEntity getUserRequired(UUID userId) {
        return userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    @Transactional
    public UserEntity updateProfile(UUID userId, String fullName, String bio) {
        try {
            UserEntity user = getUserRequired(userId);

            String normalizedName = normalizeRequiredName(fullName);
            if (normalizedName.length() > FULL_NAME_MAX) {
                throw new NameTooLongException();
            }

            String normalizedBio = normalizeOptionalText(bio);
            if (normalizedBio != null && normalizedBio.length() > BIO_MAX) {
                throw new BioTooLongException();
            }

            user.setFullName(normalizedName);
            user.setBio(normalizedBio);
            user.setUpdatedAt(Instant.now());

            UserEntity saved = userRepo.saveAndFlush(user);
            log.info("Profile updated userId={}", userId);
            return saved;
        } catch (DataAccessException ex) {
            log.error("Profile update failed - server error userId={}", userId, ex);
            throw new ProfileUpdateDbException();
        }
    }

    @Transactional
    public UserEntity uploadAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarFormatException();
        }

        long size = file.getSize();
        if (size > AVATAR_MAX_BYTES) {
            throw new AvatarTooLargeException();
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CT.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidAvatarFormatException();
        }

        UserEntity user;
        try {
            user = getUserRequired(userId);
        } catch (DataAccessException ex) {
            log.error("Avatar upload failed - server error userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.UPLOAD);
        }

        String oldKey = user.getAvatarKey();

        try {
            var stored = avatarStorage.save(
                userId,
                contentType,
                file.getOriginalFilename(),
                size,
                file.getInputStream()
            );

            Instant now = Instant.now();
            user.setAvatarKey(stored.key());
            user.setAvatarContentType(stored.contentType());
            user.setAvatarUpdatedAt(now);
            user.setUpdatedAt(now);

            UserEntity saved = userRepo.saveAndFlush(user);

            // best-effort delete old after DB commit attempt
            if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(stored.key())) {
                try {
                    avatarStorage.delete(oldKey);
                } catch (IOException ignored) {
                    log.warn("Avatar old file cleanup failed userId={} oldKey={}", userId, oldKey);
                }
            }

            log.info("Avatar updated userId={}", userId);
            return saved;
        } catch (IOException ex) {
            log.error("Avatar upload failed - server error userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.UPLOAD);
        } catch (DataAccessException ex) {
            log.error("Avatar upload failed - server error userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.UPLOAD);
        }
    }

    @Transactional
    public void deleteAvatar(UUID userId) {
        UserEntity user;
        try {
            user = getUserRequired(userId);
        } catch (DataAccessException ex) {
            log.error("Avatar deletion failed - server error userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.DELETE);
        }

        String key = user.getAvatarKey();
        if (key == null || key.isBlank()) {
            // no-op
            log.info("Avatar deleted userId={} (noop)", userId);
            return;
        }

        try {
            Instant now = Instant.now();
            user.setAvatarKey(null);
            user.setAvatarContentType(null);
            user.setAvatarUpdatedAt(null);
            user.setUpdatedAt(now);

            userRepo.saveAndFlush(user);

            try {
                avatarStorage.delete(key);
            } catch (IOException ignored) {
                log.warn("Avatar file delete failed (best-effort) userId={} key={}", userId, key);
            }

            log.info("Avatar deleted userId={}", userId);
        } catch (DataAccessException ex) {
            log.error("Avatar deletion failed - server error userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.DELETE);
        }
    }

    @Transactional(readOnly = true)
    public AvatarLoadResult loadAvatar(UUID userId) {
        UserEntity user = getUserRequired(userId);
        if (user.getAvatarKey() == null || user.getAvatarKey().isBlank()) {
            return null;
        }
        try {
            var dl = avatarStorage.open(user.getAvatarKey());
            String ct = user.getAvatarContentType() != null ? user.getAvatarContentType() : dl.contentType();
            return new AvatarLoadResult(dl.resource(), ct, dl.sizeBytes());
        } catch (IOException ex) {
            log.error("Avatar load failed userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.LOAD);
        } catch (DataAccessException ex) {
            log.error("Avatar load failed userId={}", userId, ex);
            throw new AvatarServerException(AvatarOp.LOAD);
        }
    }

    public record AvatarLoadResult(Resource resource, String contentType, long sizeBytes) { }

    private String normalizeRequiredName(String fullName) {
        if (fullName == null) return "";
        String v = fullName.trim();
        return v;
    }

    private String normalizeOptionalText(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    public static class ProfileUpdateDbException extends RuntimeException { }

    public static class NameTooLongException extends RuntimeException { }
    public static class BioTooLongException extends RuntimeException { }

    public static class InvalidAvatarFormatException extends RuntimeException { }
    public static class AvatarTooLargeException extends RuntimeException { }

    public enum AvatarOp { UPLOAD, DELETE, LOAD }

    public static class AvatarServerException extends RuntimeException {
        private final AvatarOp op;

        public AvatarServerException(AvatarOp op) {
            this.op = op;
        }

        public AvatarOp op() {
            return op;
        }
    }
}
