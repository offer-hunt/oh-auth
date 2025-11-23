package com.offerhunt.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final ObjectMapper objectMapper;
    private final String frontendErrorRedirect;

    public OAuth2LoginFailureHandler(
        ObjectMapper objectMapper,
        @Value("${app.oauth2.error-redirect}") String frontendErrorRedirect
    ) {
        this.objectMapper = objectMapper;
        this.frontendErrorRedirect = frontendErrorRedirect;
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {

        boolean json = isJsonRequest(request);
        String uri = request.getRequestURI();
        String message;
        if (uri.contains("google")) {
            log.warn("Google OAuth failed – access denied", exception);
            message = "Ошибка входа через Google.";
        } else if (uri.contains("github")) {
            log.warn("GitHub OAuth failed – access denied", exception);
            message = "Ошибка входа через GitHub.";
        } else {
            // fallback
            log.warn("OAuth2 login failed – access denied", exception);
            message = "Ошибка входа через внешний провайдер.";
        }

        if (json) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
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

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xrw = request.getHeader("X-Requested-With");
        return xrw != null && "XMLHttpRequest".equalsIgnoreCase(xrw);
    }
}
