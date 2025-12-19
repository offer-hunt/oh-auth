package com.offerhunt.auth.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProfileUpdateIT extends PostgresTCBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepo userRepo;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepo.deleteAll();

        userId = UUID.randomUUID();
        userRepo.saveAndFlush(new UserEntity(
            userId,
            "user@example.com",
            "hash",
            "Old Name"
        ));
    }

    @Test
    void updateProfile_success_andThenGetProfile_returnsUpdatedData() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "fullName", "New Name",
            "bio", "Hello, I am Denis"
        ));

        mockMvc.perform(
                put("/api/profile")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Изменения сохранены"));

        mockMvc.perform(
                get("/api/profile")
                    .with(userJwt(userId))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.fullName").value("New Name"))
            .andExpect(jsonPath("$.bio").value("Hello, I am Denis"));
    }

    @Test
    void updateProfile_emptyName_returns400() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "fullName", "   ",
            "bio", "text"
        ));

        mockMvc.perform(
                put("/api/profile")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Имя не может быть пустым"));
    }

    @Test
    void updateProfile_nameTooLong_returns400() throws Exception {
        String tooLong = "a".repeat(51);

        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "fullName", tooLong,
            "bio", "ok"
        ));

        mockMvc.perform(
                put("/api/profile")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Имя не может быть длиннее 50 символов"));
    }

    @Test
    void updateProfile_bioTooLong_returns400() throws Exception {
        String tooLong = "b".repeat(501);

        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "fullName", "Valid Name",
            "bio", tooLong
        ));

        mockMvc.perform(
                put("/api/profile")
                    .with(userJwt(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Описание не может быть длиннее 500 символов"));
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