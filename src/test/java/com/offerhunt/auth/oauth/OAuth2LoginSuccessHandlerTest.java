package com.offerhunt.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.service.SsoLoginService;
import com.offerhunt.auth.domain.service.SsoLoginService.LoginResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    SsoLoginService ssoLoginService;

    @Mock
    OAuth2AuthorizedClientService clientService;

    @Mock
    GithubEmailService githubEmailService;

    ObjectMapper objectMapper = new ObjectMapper();

    OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(
            ssoLoginService,
            objectMapper,
            clientService,
            githubEmailService,
            "http://localhost:3000/auth/callback",
            "http://localhost:3000/auth/error"
        );
    }

    @Test
    void googleExistingUser_jsonSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        TokenResponse token = new TokenResponse("Bearer", "access123", 900, "refresh123");
        when(ssoLoginService.login(any(SsoLoginService.SsoProfile.class)))
            .thenReturn(new LoginResult(token, false));

        var oauthUser = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("email", "user@example.com", "name", "User Name", "sub", "sub123"),
            "email"
        );

        var auth = new OAuth2AuthenticationToken(
            oauthUser,
            oauthUser.getAuthorities(),
            "google"
        );

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/login/oauth2/code/google");
        req.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        MockHttpServletResponse resp = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(req, resp, auth);

        assertThat(resp.getStatus()).isEqualTo(200);
        String body = resp.getContentAsString();
        assertThat(body).contains("\"access_token\":\"access123\"");
        assertThat(body).contains("\"refresh_token\":\"refresh123\"");
    }
}