package com.offerhunt.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.dao.SsoAccountRepo;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.SsoAccount;
import com.offerhunt.auth.domain.model.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SsoLoginServiceTest {

    @Mock
    UserRepo userRepo;

    @Mock
    SsoAccountRepo ssoAccountRepo;

    @Mock
    UserService userService;

    SsoLoginService service;

    @BeforeEach
    void setUp() {
        service = new SsoLoginService(userRepo, ssoAccountRepo, userService);
    }

    private SsoLoginService.SsoProfile googleProfile() {
        return new SsoLoginService.SsoProfile(
            "google",
            "sub-123",
            "user@example.com",
            true,
            "User Name"
        );
    }


    @Test
    void login_existingSsoAccount_usesLinkedUser() {
        var profile = googleProfile();

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, profile.email(), null, profile.displayName());

        SsoAccount ssoAccount = new SsoAccount(
            profile.provider(),
            profile.providerUserId(),
            user,
            profile.email(),
            profile.emailVerified(),
            null,
            null
        );

        when(ssoAccountRepo.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()))
            .thenReturn(Optional.of(ssoAccount));

        TokenResponse token = new TokenResponse("Bearer", "access123", 900, "refresh123");
        when(userService.mintTokens(userId, "USER")).thenReturn(token);

        // act
        SsoLoginService.LoginResult result = service.login(profile);

        // assert
        assertThat(result.newUser()).isFalse();
        assertThat(result.tokens()).isEqualTo(token);

        // userRepo не дергается
        verify(userRepo, never()).findByEmail(any());
        verify(userRepo, never()).saveAndFlush(any());

        // не создаём новую привязку
        verify(ssoAccountRepo, never()).saveAndFlush(any(SsoAccount.class));

        // lastLoginAt должен обновиться (проверяем, что сервис хоть что-то написал)
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(ssoAccount.getLastLoginAt()).isNotNull();
    }

    // 2) existing email: sso-привязки нет, но есть пользователь с таким email -> создаём привязку

    @Test
    void login_existingEmail_createsSsoAccount() {
        var profile = googleProfile();

        when(ssoAccountRepo.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()))
            .thenReturn(Optional.empty());

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, profile.email().toLowerCase(), null, profile.displayName());
        when(userRepo.findByEmail(profile.email().toLowerCase()))
            .thenReturn(Optional.of(user));

        // при создании привязки просто возвращаем то, что нам дали
        when(ssoAccountRepo.saveAndFlush(any(SsoAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse token = new TokenResponse("Bearer", "access456", 900, "refresh456");
        when(userService.mintTokens(userId, "USER")).thenReturn(token);

        // act
        SsoLoginService.LoginResult result = service.login(profile);

        // assert
        assertThat(result.newUser()).isFalse();
        assertThat(result.tokens()).isEqualTo(token);

        // пользователь не создавался заново
        verify(userRepo, never()).saveAndFlush(any(UserEntity.class));

        // но должна появиться новая sso-привязка
        ArgumentCaptor<SsoAccount> captor = ArgumentCaptor.forClass(SsoAccount.class);
        verify(ssoAccountRepo).saveAndFlush(captor.capture());

        SsoAccount saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(profile.provider());
        assertThat(saved.getProviderUserId()).isEqualTo(profile.providerUserId());
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getEmailAtProvider()).isEqualTo(profile.email());
    }

    // 3) new user: нет ни sso-привязки, ни пользователя по email -> создаём user + sso

    @Test
    void login_newUser_createsUserAndSsoAccount() {
        var profile = googleProfile();

        when(ssoAccountRepo.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()))
            .thenReturn(Optional.empty());

        when(userRepo.findByEmail(profile.email().toLowerCase()))
            .thenReturn(Optional.empty());

        // сохранили нового пользователя — возвращаем того же, кого дали
        when(userRepo.saveAndFlush(any(UserEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(ssoAccountRepo.saveAndFlush(any(SsoAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse token = new TokenResponse("Bearer", "access-new", 900, "refresh-new");
        when(userService.mintTokens(any(UUID.class), any()))
            .thenReturn(token);

        // act
        SsoLoginService.LoginResult result = service.login(profile);

        // assert
        assertThat(result.newUser()).isTrue();
        assertThat(result.tokens()).isEqualTo(token);

        // пользователь создан
        verify(userRepo).saveAndFlush(any(UserEntity.class));

        // и sso-привязка тоже
        verify(ssoAccountRepo).saveAndFlush(any(SsoAccount.class));
    }

    // 4) pk conflict: гонка при вставке sso-привязки -> ловим DataIntegrityViolationException,
    // перечитываем привязку и продолжаем логин без ошибки

    @Test
    void login_pkConflictOnSsoInsert_gracefullyRetries() {
        var profile = googleProfile();

        // 1-й вызов findByProviderAndProviderUserId (в начале) -> пусто
        // 2-й вызов (после конфликта PK) -> уже есть записанная другим потоком привязка
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, profile.email().toLowerCase(), null, profile.displayName());
        SsoAccount existingAccount = new SsoAccount(
            profile.provider(),
            profile.providerUserId(),
            user,
            profile.email(),
            profile.emailVerified(),
            null,
            null
        );

        when(ssoAccountRepo.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()))
            .thenReturn(Optional.empty(), Optional.of(existingAccount));

        when(userRepo.findByEmail(profile.email().toLowerCase()))
            .thenReturn(Optional.of(user));

        // первая попытка вставки sso-привязки падает с конфликтом PK
        when(ssoAccountRepo.saveAndFlush(any(SsoAccount.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        TokenResponse token = new TokenResponse("Bearer", "access-conflict", 900, "refresh-conflict");
        when(userService.mintTokens(userId, "USER")).thenReturn(token);

        // act
        SsoLoginService.LoginResult result = service.login(profile);

        // assert
        assertThat(result.newUser()).isFalse();
        assertThat(result.tokens()).isEqualTo(token);

        // saveAndFlush пытались вызвать один раз (та самая упавшая вставка)
        verify(ssoAccountRepo).saveAndFlush(any(SsoAccount.class));

        // findByProviderAndProviderUserId был вызван 2 раза:
        // - до вставки
        // - после конфликта PK (graceful retry)
        verify(ssoAccountRepo, times(2))
            .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
    }
}
