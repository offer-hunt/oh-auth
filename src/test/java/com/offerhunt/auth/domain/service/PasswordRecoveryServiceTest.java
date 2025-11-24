package com.offerhunt.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.offerhunt.auth.domain.dao.PasswordResetTokenRepo;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.PasswordResetToken;
import com.offerhunt.auth.domain.model.UserEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordResetTokenRepo tokenRepo;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PasswordResetMailService mailService;

    @Mock
    PasswordResetTokenGenerator tokenGenerator;

    PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(
            userRepo,
            tokenRepo,
            passwordEncoder,
            mailService,
            tokenGenerator,
            3600L,
            "http://localhost:3000/auth/reset-password"
        );
    }

    @Test
    void initiateRecovery_existingUser_createsTokenAndSendsEmail() {
        String email = "User@Example.com";
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, email.toLowerCase(), "hash", "User");
        when(userRepo.findByEmail(email.toLowerCase())).thenReturn(Optional.of(user));
        when(tokenGenerator.generateToken()).thenReturn("RAW_TOKEN");
        when(tokenRepo.saveAndFlush(any(PasswordResetToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.initiateRecovery(email, "127.0.0.1", "JUnit");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).saveAndFlush(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        verify(mailService).sendResetLink(eq(email.toLowerCase()), contains("RAW_TOKEN"));
        // sanity check: expiresAt выставлен
        assert saved.getExpiresAt().isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void initiateRecovery_noUser_doesNotCreateToken() {
        String email = "nouser@example.com";
        when(userRepo.findByEmail(email.toLowerCase())).thenReturn(Optional.empty());

        service.initiateRecovery(email, "127.0.0.1", "JUnit");

        verify(tokenRepo, never()).saveAndFlush(any());
        verify(mailService, never()).sendResetLink(anyString(), anyString());
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndMarksUsed() {
        String rawToken = "RAW_TOKEN";
        String tokenHash = TestHashUtil.sha256Hex(rawToken);

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, "user@example.com", "old", "User");

        PasswordResetToken token = new PasswordResetToken(
            UUID.randomUUID(),
            user,
            tokenHash,
            Instant.now().plusSeconds(3600),
            "127.0.0.1",
            "JUnit"
        );

        when(tokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("ENC_HASH");

        service.resetPassword(rawToken, "NewPass1!");

        verify(passwordEncoder).encode("NewPass1!");
        verify(userRepo).save(user);
        verify(tokenRepo).save(token);
    }

    @Test
    void resetPassword_invalidToken_throws() {
        String rawToken = "RAW_TOKEN";
        String tokenHash = TestHashUtil.sha256Hex(rawToken);

        when(tokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(rawToken, "NewPass1!"))
            .isInstanceOf(PasswordRecoveryService.InvalidTokenException.class);
    }

    static class TestHashUtil {
        static String sha256Hex(String value) {
            try {
                var md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
