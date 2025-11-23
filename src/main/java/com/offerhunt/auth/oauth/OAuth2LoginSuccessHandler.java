package com.offerhunt.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import com.offerhunt.auth.domain.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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

    private final UserRepo userRepo;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final OAuth2AuthorizedClientService clientService;
    private final GithubEmailService githubEmailService;
    private final String frontendRedirect;
    private final String frontendErrorRedirect;

    public OAuth2LoginSuccessHandler(
        UserRepo userRepo,
        UserService userService,
        ObjectMapper objectMapper,
        OAuth2AuthorizedClientService clientService,
        GithubEmailService githubEmailService,
        @Value("${app.oauth2.redirect}") String frontendRedirect,
        @Value("${app.oauth2.error-redirect}") String frontendErrorRedirect
    ) {
        this.userRepo = userRepo;
        this.userService = userService;
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
        } catch (DbUnavailableException ex) {
            // уже залогировано корректной строкой
            writeError(response, json, HttpStatus.SERVICE_UNAVAILABLE, "Ошибка сервера. Попробуйте позже.");
            return;
        } catch (InsertFailedException ex) {
            // уже залогировано корректной строкой
            writeError(response, json, HttpStatus.INTERNAL_SERVER_ERROR, "Что-то пошло не так. Попробуйте позже.");
            return;
        }

        writeSuccess(response, result, json);
    }

    // --- провайдеры ---

    private LoginResult handleGoogle(OAuth2User oauth2User) {
        String email = oauth2User.<String>getAttribute("email");
        String name = oauth2User.<String>getAttribute("name");

        if (email == null) {
            log.error("Google OAuth failed – insert error: email is null in profile");
            throw new InsertFailedException();
        }
        if (name == null) {
            name = email;
        }

        log.info("Google OAuth success – data received");
        return upsertUser("Google", email, name);
    }

    private LoginResult handleGithub(OAuth2User oauth2User, OAuth2AuthorizedClient client) {
        String email = githubEmailService.resolveEmail(oauth2User, client);
        if (email == null) {
            log.error("GitHub OAuth failed – insert error: email is null");
            throw new InsertFailedException();
        }

        String name = oauth2User.<String>getAttribute("name");
        if (name == null) {
            name = oauth2User.<String>getAttribute("login");
        }
        if (name == null) {
            name = email;
        }

        log.info("GitHub OAuth success – data received");
        return upsertUser("GitHub", email, name);
    }

    private LoginResult upsertUser(String provider, String email, String name) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);

        try {
            Optional<UserEntity> existingOpt = userRepo.findByEmail(normalizedEmail);
            if (existingOpt.isPresent()) {
                UserEntity existing = existingOpt.get();
                log.info("{} OAuth success – existing user", provider);
                TokenResponse tokens = userService.mintTokens(existing.getId(), existing.getGlobalRole());
                return new LoginResult(tokens, false);
            }
        } catch (DataAccessException ex) {
            log.error("{} OAuth failed – db error", provider, ex);
            throw new DbUnavailableException();
        }

        UserEntity newUser = new UserEntity(
            UUID.randomUUID(),
            normalizedEmail,
            null,
            name
        );

        try {
            UserEntity saved = userRepo.saveAndFlush(newUser);
            log.info("{} OAuth success – new user", provider);
            TokenResponse tokens = userService.mintTokens(saved.getId(), saved.getGlobalRole());
            return new LoginResult(tokens, true);
        } catch (DataAccessException ex) {
            log.error("{} OAuth failed – insert error", provider, ex);
            throw new InsertFailedException();
        }
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xrw = request.getHeader("X-Requested-With");
        return xrw != null && "XMLHttpRequest".equalsIgnoreCase(xrw);
    }

    private void writeSuccess(HttpServletResponse response, LoginResult result, boolean json) throws IOException {
        if (json) {
            response.setStatus(result.newUser ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), result.tokens());
        } else {
            String redirect = buildSuccessRedirectUrl(result.tokens());
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

    private record LoginResult(TokenResponse tokens, boolean newUser) { }

    private static class DbUnavailableException extends RuntimeException { }

    private static class InsertFailedException extends RuntimeException { }
}
