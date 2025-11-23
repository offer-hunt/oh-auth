package com.offerhunt.auth.domain.service;

import com.offerhunt.auth.api.dto.LoginRequest;
import com.offerhunt.auth.api.dto.RegisterRequest;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.api.exception.DuplicateEmailException;
import com.offerhunt.auth.domain.dao.UserRepo;
import com.offerhunt.auth.domain.model.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepo repo;
    private final PasswordEncoder pe;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;

    @Value("${app.audience:offerhunt-api}")
    private String audience;

    @Value("${app.issuer:http://localhost:8080}")
    private String issuer;

    public UserService(
        UserRepo repo,
        PasswordEncoder pe,
        JwtEncoder jwtEncoder,
        @Qualifier("localJwtDecoder") JwtDecoder refreshJwtDecoder
    ) {
        this.repo = repo;
        this.pe = pe;
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
    }

    @Transactional
    public UUID register(RegisterRequest r) {
        final String email = r.email().toLowerCase();
        log.info("event=RegistrationInitiated email={}", email);

        if (repo.existsByEmail(email)) {
            log.info("event=RegistrationFailed reason=email_exists email={}", email);
            throw new DuplicateEmailException(email);
        }

        try {
            UserEntity saved = repo.saveAndFlush(
                new UserEntity(
                    UUID.randomUUID(),
                    email,
                    pe.encode(r.password()),
                    r.fullName()
                )
            );
            log.info("event=RegistrationSuccess userId={} email={}", saved.getId(), saved.getEmail());
            return saved.getId();
        } catch (DataIntegrityViolationException dup) {
            log.info("event=RegistrationFailed reason=email_exists email={}", email);
            throw new DuplicateEmailException(email);
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest r) {
        UserEntity u = repo.findByEmail(r.email().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (u.getPasswordHash() == null || !pe.matches(r.password(), u.getPasswordHash())) {
            throw new IllegalArgumentException("bad credentials");
        }
        return mintTokens(u.getId(), u.getGlobalRole());
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        var jwt = refreshJwtDecoder.decode(refreshToken);
        if (!"refresh".equals(jwt.getClaimAsString("typ"))) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        UUID uid = UUID.fromString(jwt.getSubject());
        String role = jwt.getClaimAsString("role");
        return mintTokens(uid, role != null ? role : "USER");
    }

    private TokenResponse mintTokens(UUID userId, String role) {
        Instant now = Instant.now();

        var access = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .subject(userId.toString())
            .audience(List.of(audience))
            .claim("scope", "api")
            .claim("role", role)
            .build();

        var refresh = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(2_592_000))
            .subject(userId.toString())
            .claim("typ", "refresh")
            .claim("role", role)
            .build();

        String at = jwtEncoder.encode(JwtEncoderParameters.from(access)).getTokenValue();
        String rt = jwtEncoder.encode(JwtEncoderParameters.from(refresh)).getTokenValue();
        return new TokenResponse("Bearer", at, 900, rt);
    }
}
