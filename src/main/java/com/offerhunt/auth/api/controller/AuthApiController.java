package com.offerhunt.auth.api.controller;

import com.offerhunt.auth.api.dto.LoginRequest;
import com.offerhunt.auth.api.dto.RegisterRequest;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.domain.service.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService users;

    public AuthApiController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r) {
        users.register(r);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "ok"));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r) {
        return users.login(r);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestParam("refresh_token") String refresh) {
        return users.refresh(refresh);
    }
}