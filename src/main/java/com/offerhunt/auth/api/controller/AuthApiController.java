package com.offerhunt.auth.api.controller;

import com.offerhunt.auth.api.dto.ForgotPasswordRequest;
import com.offerhunt.auth.api.dto.LoginRequest;
import com.offerhunt.auth.api.dto.PasswordResetRequest;
import com.offerhunt.auth.api.dto.RegisterRequest;
import com.offerhunt.auth.api.dto.TokenResponse;
import com.offerhunt.auth.api.validation.StrongPasswordValidator;
import com.offerhunt.auth.domain.service.PasswordRecoveryService;
import com.offerhunt.auth.domain.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private static final Logger log = LoggerFactory.getLogger(AuthApiController.class);

    private final UserService users;
    private final PasswordRecoveryService passwordRecoveryService;
    private final Validator validator;
    private final StrongPasswordValidator strongPasswordValidator = new StrongPasswordValidator();

    public AuthApiController(
        UserService users,
        PasswordRecoveryService passwordRecoveryService,
        Validator validator
    ) {
        this.users = users;
        this.passwordRecoveryService = passwordRecoveryService;
        this.validator = validator;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest r) {
        users.register(r);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "ok"));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest r) {
        return users.login(r);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestParam("refresh_token") String refresh) {
        return users.refresh(refresh);
    }


    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(
        @RequestBody ForgotPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.info("Password recovery failed – invalid email");
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Email некорректен"));
        }

        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        passwordRecoveryService.initiateRecovery(request.email(), ip, userAgent);

        return ResponseEntity.ok(Map.of(
            "message", "Если аккаунт существует, письмо отправлено."
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest request) {
        String token = request.token();
        String password = request.password();
        String passwordConfirmation = request.passwordConfirmation();

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Ссылка недействительна или устарела."));
        }

        if (!strongPasswordValidator.isValid(password, null)) {
            log.info("Password reset failed – weak password");
            return ResponseEntity.badRequest()
                .body(Map.of("message",
                    "Пароль слишком простой. Добавьте цифры, символы или заглавные буквы"));
        }

        if (passwordConfirmation == null || !password.equals(passwordConfirmation)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Пароли не совпадают"));
        }

        passwordRecoveryService.resetPassword(token, password);

        return ResponseEntity.ok(Map.of(
            "message", "Пароль успешно обновлён."
        ));
    }
}
