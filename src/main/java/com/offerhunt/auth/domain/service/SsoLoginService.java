package com.offerhunt.auth.domain.service;

import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.dao.SsoAccountRepo;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.SsoAccount;
import com.offerhunt.auth.domain.model.UserEntity;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SsoLoginService {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginService.class);

    private final UserRepo userRepo;
    private final SsoAccountRepo ssoAccountRepo;
    private final UserService userService;

    public SsoLoginService(UserRepo userRepo, SsoAccountRepo ssoAccountRepo, UserService userService) {
        this.userRepo = userRepo;
        this.ssoAccountRepo = ssoAccountRepo;
        this.userService = userService;
    }

    /**
     * Входной профиль SSO-пользователя.
     */
    public record SsoProfile(
        String provider,        // "google" / "github"
        String providerUserId,  // sub / id
        String email,
        boolean emailVerified,
        String displayName
    ) { }

    /**
     * Результат SSO-логина.
     */
    public record LoginResult(
        TokenResponse tokens,
        boolean newUser
    ) { }

    /**
     * Основной алгоритм (см. мини-промт): всё внутри REQUIRED транзакции.
     */
    @Transactional
    public LoginResult login(SsoProfile profile) {
        String provider = profile.provider();
        String providerForLog = "google".equals(provider)
            ? "Google"
            : "github".equals(provider) ? "GitHub" : provider;

        Instant now = Instant.now();
        String normalizedEmail = profile.email().toLowerCase(Locale.ROOT);

        // a) ищем привязку по (provider, provider_user_id)
        SsoAccount ssoAccount;
        try {
            ssoAccount = ssoAccountRepo
                .findByProviderAndProviderUserId(provider, profile.providerUserId())
                .orElse(null);
        } catch (DataAccessException ex) {
            log.error("{} OAuth failed – db error", providerForLog, ex);
            throw new DbUnavailableException();
        }

        if (ssoAccount != null) {
            UserEntity user = ssoAccount.getUser();
            touchLogin(user, ssoAccount, now, normalizedEmail, profile.emailVerified());
            TokenResponse tokens = userService.mintTokens(user.getId(), user.getGlobalRole());
            log.info("{} OAuth success – existing user", providerForLog);
            return new LoginResult(tokens, false);
        }

        // b) привязки нет — ищем пользователя по email
        UserEntity user;
        try {
            user = userRepo.findByEmail(normalizedEmail).orElse(null);
        } catch (DataAccessException ex) {
            log.error("{} OAuth failed – db error", providerForLog, ex);
            throw new DbUnavailableException();
        }

        boolean newUser = false;

        if (user == null) {
            // создаём нового пользователя
            user = new UserEntity(
                UUID.randomUUID(),
                normalizedEmail,
                null,
                profile.displayName()
            );
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setLastLoginAt(now);
            if (profile.emailVerified()) {
                user.setEmailVerifiedAt(now);
            }

            try {
                user = userRepo.saveAndFlush(user);
            } catch (DataAccessException ex) {
                log.error("{} OAuth failed – insert error", providerForLog, ex);
                throw new InsertFailedException();
            }

            newUser = true;
        } else {
            // существующий пользователь по email
            touchUserLogin(user, now, normalizedEmail, profile.emailVerified());
        }

        try {
            createSsoAccount(profile, user, now);
        } catch (DataIntegrityViolationException ex) {
            try {
                Optional<SsoAccount> existing =
                    ssoAccountRepo.findByProviderAndProviderUserId(provider, profile.providerUserId());
                if (existing.isPresent()) {
                    touchLogin(user, existing.get(), now, normalizedEmail, profile.emailVerified());
                } else {
                    log.error("{} OAuth failed – insert error", providerForLog, ex);
                    throw new InsertFailedException();
                }
            } catch (DataAccessException ex2) {
                log.error("{} OAuth failed – db error", providerForLog, ex2);
                throw new DbUnavailableException();
            }
        } catch (DataAccessException ex) {
            log.error("{} OAuth failed – insert error", providerForLog, ex);
            throw new InsertFailedException();
        }

        if (newUser) {
            log.info("{} OAuth success – new user", providerForLog);
        } else {
            log.info("{} OAuth success – existing user", providerForLog);
        }

        TokenResponse tokens = userService.mintTokens(user.getId(), user.getGlobalRole());
        return new LoginResult(tokens, newUser);
    }

    private void createSsoAccount(SsoProfile profile, UserEntity user, Instant now) {
        SsoAccount account = new SsoAccount(
            profile.provider(),
            profile.providerUserId(),
            user,
            profile.email(),
            profile.emailVerified(),
            now,
            now
        );
        ssoAccountRepo.saveAndFlush(account);
        touchUserLogin(user, now, profile.email().toLowerCase(Locale.ROOT), profile.emailVerified());
    }

    private void touchLogin(
        UserEntity user,
        SsoAccount account,
        Instant now,
        String normalizedEmail,
        boolean emailVerified
    ) {
        account.setLastLoginAt(now);
        touchUserLogin(user, now, normalizedEmail, emailVerified);
    }

    private void touchUserLogin(
        UserEntity user,
        Instant now,
        String normalizedEmail,
        boolean emailVerified
    ) {
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        if (emailVerified
            && user.getEmail() != null
            && normalizedEmail.equalsIgnoreCase(user.getEmail())
            && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
    }

    public static class DbUnavailableException extends RuntimeException { }

    public static class InsertFailedException extends RuntimeException { }
}
