package com.offerhunt.auth.domain.service;

import com.offerhunt.auth.domain.dao.PasswordResetTokenRepo;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.PasswordResetToken;
import com.offerhunt.auth.domain.model.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final UserRepo userRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailService mailService;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final long ttlSeconds;
    private final String resetBaseUrl;

    public PasswordRecoveryService(
        UserRepo userRepo,
        PasswordResetTokenRepo passwordResetTokenRepo,
        PasswordEncoder passwordEncoder,
        PasswordResetMailService mailService,
        PasswordResetTokenGenerator tokenGenerator,
        @Value("${app.auth.password-reset.ttl-seconds:3600}") long ttlSeconds,
        @Value("${app.password-reset.base-url:http://localhost:3000/auth/reset-password}") String resetBaseUrl
    ) {
        this.userRepo = userRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.tokenGenerator = tokenGenerator;
        this.ttlSeconds = ttlSeconds;
        this.resetBaseUrl = resetBaseUrl;
    }

    /**
     * Инициация восстановления пароля.
     */
    @Transactional
    public void initiateRecovery(String email, String requestIp, String userAgent) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        UserEntity user;
        try {
            user = userRepo.findByEmail(normalizedEmail).orElse(null);
        } catch (DataAccessException ex) {
            log.error("Password recovery failed – db error", ex);
            throw new PasswordRecoveryDbException();
        }

        if (user == null) {
            // Не раскрываем, что пользователя нет
            log.info("Password recovery – safe no user");
            return;
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        String rawToken = tokenGenerator.generateToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(
            UUID.randomUUID(),
            user,
            tokenHash,
            expiresAt,
            requestIp,
            userAgent
        );

        try {
            passwordResetTokenRepo.saveAndFlush(token);
        } catch (DataAccessException ex) {
            log.error("Password recovery failed – db error", ex);
            throw new PasswordRecoveryDbException();
        }

        String url = UriComponentsBuilder.fromUriString(resetBaseUrl)
            .queryParam("token", rawToken)
            .build(true)
            .toUriString();

        mailService.sendResetLink(user.getEmail(), url);
        log.info("Password recovery – email sent");
    }

    /**
     * Сброс пароля по токену.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token;
        try {
            token = passwordResetTokenRepo.findByTokenHash(tokenHash).orElse(null);
        } catch (DataAccessException ex) {
            log.error("Password reset failed – db error", ex);
            throw new PasswordResetDbException();
        }

        Instant now = Instant.now();

        if (token == null
            || (token.getExpiresAt() != null && token.getExpiresAt().isBefore(now))
            || token.getUsedAt() != null) {

            log.info("Password reset failed – invalid token");
            throw new InvalidTokenException();
        }

        UserEntity user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        token.setUsedAt(now);

        try {
            // благодаря @Transactional и managed-сущностям можно было бы не звать save(),
            // но явно вызываем, чтобы отловить DataAccessException
            userRepo.save(user);
            passwordResetTokenRepo.save(token);
        } catch (DataAccessException ex) {
            log.error("Password reset failed – db error", ex);
            throw new PasswordResetDbException();
        }

        log.info("Password reset success");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // --- доменные исключения для GlobalExceptionHandler ---

    public static class PasswordRecoveryDbException extends RuntimeException {
    }

    public static class PasswordResetDbException extends RuntimeException {
    }

    public static class InvalidTokenException extends RuntimeException {
    }
}
