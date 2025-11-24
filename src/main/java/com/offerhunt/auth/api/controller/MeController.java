package com.offerhunt.auth.api.controller;

import java.util.Map;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, Object> me(JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        return Map.of(
                "userId", jwt.getSubject(),
                "role", jwt.getClaimAsString("role"),
                "aud", jwt.getAudience(),
                "iss", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null
        );
    }
}
