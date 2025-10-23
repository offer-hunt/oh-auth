package com.offerhunt.auth.api.controller;

import com.offerhunt.auth.api.dto.LoginRequest;
import com.offerhunt.auth.api.dto.RegisterRequest;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.model.UserEntity;
import com.offerhunt.auth.service.UserService;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder localJwtDecoder;

    public AuthApiController(
        UserService users,
        PasswordEncoder encoder,
        JwtEncoder jwtEncoder,
        @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
        @Qualifier("localJwtDecoder") JwtDecoder localJwtDecoder
    ) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.localJwtDecoder = localJwtDecoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r) {
        users.createLocal(r.email(), encoder.encode(r.password()), r.fullName());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r) {
        UserEntity u = users.verifyCredentials(r.email(), r.password());
        return mintTokens(u.getId(), u.getGlobalRole());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestParam("refresh_token") String refresh) {
        var jwt = localJwtDecoder.decode(refresh);
        if (!"refresh".equals(jwt.getClaimAsString("typ"))) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        UUID uid = UUID.fromString(jwt.getSubject());
        String role = jwt.getClaimAsString("role");
        return mintTokens(uid, role != null ? role : "USER");
    }

    private TokenResponse mintTokens(UUID userId, String role) {
        Instant now = Instant.now();

        var accessClaims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .subject(userId.toString())
            .audience(List.of("offerhunt-api"))
            .claim("scope", "api")
            .claim("role", role)
            .build();

        var refreshClaims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(2592000))
            .subject(userId.toString())
            .claim("typ", "refresh")
            .claim("role", role)
            .build();

        String at = jwtEncoder.encode(JwtEncoderParameters.from(accessClaims)).getTokenValue();
        String rt = jwtEncoder.encode(JwtEncoderParameters.from(refreshClaims)).getTokenValue();
        return new TokenResponse("Bearer", at, 900, rt);
    }
}
