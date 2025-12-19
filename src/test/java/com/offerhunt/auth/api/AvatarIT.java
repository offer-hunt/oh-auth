package com.offerhunt.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import com.offerhunt.auth.support.PostgresTCBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.security.enabled=true",

    "spring.security.oauth2.client.registration.google.client-id=test",
    "spring.security.oauth2.client.registration.google.client-secret=test",
    "spring.security.oauth2.client.registration.github.client-id=test",
    "spring.security.oauth2.client.registration.github.client-secret=test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvatarIT extends PostgresTCBase {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepo userRepo;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepo.deleteAll();

        userId = UUID.randomUUID();
        UserEntity u = new UserEntity(
            userId,
            "user@example.com",
            "hash",
            "User"
        );
        userRepo.saveAndFlush(u);
    }

    private RequestPostProcessor auth() {
        // Jwt.Builder#issuer принимает String (не URI)
        return jwt().jwt(j -> j
            .subject(userId.toString())
            .claim("role", "USER")
            .issuer("http://test-issuer")
        );
    }

    @Test
    void uploadAvatar_success_profileHasAvatarUrl_andAvatarIsDownloadable() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            tinyPngBytes()
        );

        // upload
        mockMvc.perform(
                multipart("/api/profile/avatar")
                    .file(png)
                    .with(auth())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is2xxSuccessful());

        String avatarUrl = extractAvatarUrlFromProfile(getProfile());

        assertThat(avatarUrl)
            .as("profile must contain avatar url after upload")
            .isNotBlank();

        // download by avatarUrl should work
        mockMvc.perform(
                get(toMockMvcPath(avatarUrl))
                    .with(auth())
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/")))
            .andExpect(result ->
                assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty()
            );
    }

    @Test
    void uploadAvatar_invalidFormat_returns400() throws Exception {
        MockMultipartFile gif = new MockMultipartFile(
            "file",
            "avatar.gif",
            "image/gif",
            "GIF89a".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                multipart("/api/profile/avatar")
                    .file(gif)
                    .with(auth())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAvatar_tooLarge_returns400() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1]; // 5MB + 1 byte

        MockMultipartFile png = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            big
        );

        mockMvc.perform(
                multipart("/api/profile/avatar")
                    .file(png)
                    .with(auth())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAvatar_success_oldAvatarBecomesUnavailable_andProfileHasNoAvatarUrl() throws Exception {
        // upload first
        MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png", tinyPngBytes());
        mockMvc.perform(multipart("/api/profile/avatar").file(png).with(auth()))
            .andExpect(status().is2xxSuccessful());

        String avatarUrl = extractAvatarUrlFromProfile(getProfile());
        assertThat(avatarUrl).isNotBlank();

        // sanity: downloadable before delete
        mockMvc.perform(get(toMockMvcPath(avatarUrl)).with(auth()))
            .andExpect(status().isOk());

        // delete
        mockMvc.perform(delete("/api/profile/avatar").with(auth()))
            .andExpect(status().is2xxSuccessful());

        // after delete: old url should be 404/410 (зависит от твоей реализации)
        mockMvc.perform(get(toMockMvcPath(avatarUrl)).with(auth()))
            .andExpect(status().is4xxClientError());

        // profile after delete: avatarUrl null/empty/absent (зависит от контракта)
        JsonNode profile = getProfile();
        String maybeUrl = findAvatarUrl(profile);
        assertThat(maybeUrl).as("avatarUrl should be empty after delete").isNull();
    }

    private JsonNode getProfile() throws Exception {
        String body = mockMvc.perform(get("/api/profile").with(auth()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(body);
    }

    private String extractAvatarUrlFromProfile(JsonNode profileJson) {
        String url = findAvatarUrl(profileJson);
        assertThat(url).as("avatarUrl field is missing or empty in profile response").isNotBlank();
        return url;
    }

    /**
     * Делает тест устойчивым к имени поля:
     * - avatarUrl
     * - avatar_url
     * Если у тебя иначе — добавь сюда ещё вариант.
     */
    private String findAvatarUrl(JsonNode profileJson) {
        if (profileJson == null || profileJson.isNull()) {
            return null;
        }

        JsonNode v1 = profileJson.get("avatarUrl");
        if (v1 != null && !v1.isNull() && !v1.asText().isBlank()) {
            return v1.asText();
        }

        JsonNode v2 = profileJson.get("avatar_url");
        if (v2 != null && !v2.isNull() && !v2.asText().isBlank()) {
            return v2.asText();
        }

        return null;
    }


    private String toMockMvcPath(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }
        if (avatarUrl.startsWith("/")) {
            return avatarUrl;
        }

        URI uri = URI.create(avatarUrl);
        String path = uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            path = path + "?" + uri.getRawQuery();
        }
        return path;
    }

    /**
     * Мини-PNG 1x1, валидный. (base64)
     */
    private byte[] tinyPngBytes() {
        String b64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/6X8l1UAAAAASUVORK5CYII=";
        return Base64.getDecoder().decode(b64);
    }
}