package com.offerhunt.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import com.offerhunt.auth.support.PostgresTCBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.enabled=true",
    "app.security.use-local-key=true",
    "app.issuer=http://localhost:8080",
    "app.audience=offerhunt-api"
})
class ChangePasswordIT extends PostgresTCBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepo userRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepo.deleteAll();

        userId = UUID.randomUUID();
        UserEntity u = new UserEntity(
            userId,
            "user@example.com",
            passwordEncoder.encode("OldPass1!"),
            "User"
        );
        userRepo.saveAndFlush(u);
    }

    @Test
    void changePassword_success_updatesHash() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "currentPassword", "OldPass1!",
            "newPassword", "NewPass2!",
            "newPasswordConfirmation", "NewPass2!"
        ));

        mockMvc.perform(
                post("/api/auth/password/change")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Пароль успешно изменен"));

        UserEntity saved = userRepo.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass2!", saved.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPass1!", saved.getPasswordHash())).isFalse();
    }

    @Test
    void changePassword_incorrectCurrent_returns400() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "currentPassword", "WRONG",
            "newPassword", "NewPass2!",
            "newPasswordConfirmation", "NewPass2!"
        ));

        mockMvc.perform(
                post("/api/auth/password/change")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Текущий пароль неверный"));
    }

    @Test
    void changePassword_weakNewPassword_returns400() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "currentPassword", "OldPass1!",
            "newPassword", "weak",
            "newPasswordConfirmation", "weak"
        ));

        mockMvc.perform(
                post("/api/auth/password/change")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "Пароль слишком простой. Добавьте цифры, символы или заглавные буквы"
            ));
    }

    @Test
    void changePassword_confirmationMismatch_returns400() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "currentPassword", "OldPass1!",
            "newPassword", "NewPass2!",
            "newPasswordConfirmation", "NewPass3!"
        ));

        mockMvc.perform(
                post("/api/auth/password/change")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Пароли не совпадают"));
    }

    @Test
    void changePassword_emptyCurrent_returns400() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "currentPassword", "   ",
            "newPassword", "NewPass2!",
            "newPasswordConfirmation", "NewPass2!"
        ));

        mockMvc.perform(
                post("/api/auth/password/change")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Текущий пароль обязателен"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(UUID uid) {
        return jwt().jwt(j -> j
            .subject(uid.toString())
            .issuer("http://localhost:8080")
            .audience(List.of("offerhunt-api"))
            .claim("role", "USER")
        );
    }
}
