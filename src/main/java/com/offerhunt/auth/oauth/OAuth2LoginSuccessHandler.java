package com.offerhunt.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.service.SsoLoginService;
import com.offerhunt.auth.domain.service.SsoLoginService.LoginResult;
import com.offerhunt.auth.domain.service.SsoLoginService.SsoProfile;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final SsoLoginService ssoLoginService;
    private final ObjectMapper objectMapper;
    private final OAuth2AuthorizedClientService clientService;
    private final GithubEmailService githubEmailService;
    private final String frontendRedirect;
    private final String frontendErrorRedirect;

    public OAuth2LoginSuccessHandler(
        SsoLoginService ssoLoginService,
        ObjectMapper objectMapper,
        OAuth2AuthorizedClientService clientService,
        GithubEmailService githubEmailService,
        @Value("${app.oauth2.redirect}") String frontendRedirect,
        @Value("${app.oauth2.error-redirect}") String frontendErrorRedirect
    ) {
        this.ssoLoginService = ssoLoginService;
        this.objectMapper = objectMapper;
        this.clientService = clientService;
        this.githubEmailService = githubEmailService;
        this.frontendRedirect = frontendRedirect;
        this.frontendErrorRedirect = frontendErrorRedirect;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Unsupported authentication");
            return;
        }

        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = oauthToken.getPrincipal();
        boolean json = isJsonRequest(request);

        LoginResult result;
        try {
            if ("google".equals(registrationId)) {
                result = handleGoogle(oauth2User);
            } else if ("github".equals(registrationId)) {
                OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                    registrationId,
                    oauthToken.getName()
                );
                result = handleGithub(oauth2User, client);
            } else {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Unsupported provider");
                return;
            }
        } catch (SsoLoginService.DbUnavailableException ex) {
            writeError(response, json, HttpStatus.SERVICE_UNAVAILABLE, "Ошибка сервера. Попробуйте позже.");
            return;
        } catch (SsoLoginService.InsertFailedException ex) {
            writeError(response, json, HttpStatus.INTERNAL_SERVER_ERROR, "Что-то пошло не так. Попробуйте позже.");
            return;
        }

        writeSuccess(response, result, json);
    }

    // --- провайдеры ---

    private LoginResult handleGoogle(OAuth2User oauth2User) {
        String email = oauth2User.<String>getAttribute("email");
        String name = oauth2User.<String>getAttribute("name");
        String sub = oauth2User.<String>getAttribute("sub");
        Boolean emailVerifiedAttr = oauth2User.<Boolean>getAttribute("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedAttr);

        if (email == null) {
            log.error("Google OAuth failed – insert error: email is null in profile");
            throw new SsoLoginService.InsertFailedException();
        }
        if (name == null) {
            name = email;
        }
        if (sub == null) {
            // fallback, чтобы не завалиться на null PK
            sub = email;
        }

        log.info("Google OAuth success – data received");

        SsoProfile profile = new SsoProfile(
            "google",
            sub,
            email,
            emailVerified,
            name
        );

        return ssoLoginService.login(profile);
    }

    private LoginResult handleGithub(OAuth2User oauth2User, OAuth2AuthorizedClient client) {
        GithubEmailService.GithubEmail resolved = githubEmailService.resolveEmail(oauth2User, client);
        if (resolved == null || resolved.email() == null) {
            log.error("GitHub OAuth failed – insert error: email is null");
            throw new SsoLoginService.InsertFailedException();
        }

        String email = resolved.email();
        boolean emailVerified = resolved.verified();

        String name = oauth2User.<String>getAttribute("name");
        if (name == null) {
            name = oauth2User.<String>getAttribute("login");
        }
        if (name == null) {
            name = email;
        }

        Object idAttr = oauth2User.getAttribute("id");
        String providerUserId = idAttr != null ? String.valueOf(idAttr) : oauth2User.<String>getAttribute("login");
        if (providerUserId == null) {
            providerUserId = email;
        }

        log.info("GitHub OAuth success – data received");

        SsoProfile profile = new SsoProfile(
            "github",
            providerUserId,
            email,
            emailVerified,
            name
        );

        return ssoLoginService.login(profile);
    }

    // --- вывод ответа / редиректы ---

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xrw = request.getHeader("X-Requested-With");
        return xrw != null && "XMLHttpRequest".equalsIgnoreCase(xrw);
    }

    private void writeSuccess(HttpServletResponse response, LoginResult result, boolean json) throws IOException {
        TokenResponse tokens = result.tokens();
        if (json) {
            response.setStatus(result.newUser() ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), tokens);
        } else {
            String redirect = buildSuccessRedirectUrl(tokens);
            response.sendRedirect(redirect);
        }
    }

    private String buildSuccessRedirectUrl(TokenResponse tokens) {
        String fragment = String.format(
            "access_token=%s&expires_in=%d&refresh_token=%s",
            UriUtils.encode(tokens.access_token(), StandardCharsets.UTF_8),
            tokens.expires_in(),
            UriUtils.encode(tokens.refresh_token(), StandardCharsets.UTF_8)
        );
        return UriComponentsBuilder.fromUriString(frontendRedirect)
            .fragment(fragment)
            .build(true)
            .toUriString();
    }

    private void writeError(HttpServletResponse response, boolean json, HttpStatus status, String message)
        throws IOException {

        if (json) {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of("message", message));
        } else {
            String redirect = UriComponentsBuilder.fromUriString(frontendErrorRedirect)
                .queryParam("message", UriUtils.encode(message, StandardCharsets.UTF_8))
                .build(true)
                .toUriString();
            response.sendRedirect(redirect);
        }
    }
}