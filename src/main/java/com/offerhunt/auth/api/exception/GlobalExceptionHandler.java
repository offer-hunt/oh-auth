package com.offerhunt.auth.api.exception;

import com.offerhunt.auth.api.dto.ErrorResponse;
import com.offerhunt.auth.domain.service.PasswordRecoveryService;
import com.offerhunt.auth.domain.service.UserProfileService;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", "Validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("EMAIL_EXISTS", "Email is already registered"));
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ErrorResponse> handleDbDown(RuntimeException ex) {
        log.error("Registration failed – server error", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.of("DB_UNAVAILABLE", "Server error. Please try later."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(PasswordRecoveryService.PasswordRecoveryDbException.class)
    public ResponseEntity<Map<String, String>> handlePasswordRecoveryDb(
        PasswordRecoveryService.PasswordRecoveryDbException ex
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Ошибка сервера. Попробуйте позже."));
    }

    @ExceptionHandler(PasswordRecoveryService.PasswordResetDbException.class)
    public ResponseEntity<Map<String, String>> handlePasswordResetDb(
        PasswordRecoveryService.PasswordResetDbException ex
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Ошибка сервера. Попробуйте позже."));
    }

    @ExceptionHandler(PasswordRecoveryService.InvalidTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidResetToken(
        PasswordRecoveryService.InvalidTokenException ex
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "Ссылка недействительна или устарела."));
    }

    @ExceptionHandler(UserProfileService.ProfileUpdateDbException.class)
    public ResponseEntity<Map<String, String>> handleProfileUpdateDb(UserProfileService.ProfileUpdateDbException ex) {
        log.info("Profile update failed - server error");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Не удалось сохранить изменения. Попробуйте позже"));
    }

    @ExceptionHandler(UserProfileService.AvatarServerException.class)
    public ResponseEntity<Map<String, String>> handleAvatarServer(UserProfileService.AvatarServerException ex) {
        String msg = switch (ex.op()) {
            case UPLOAD -> "Не удалось загрузить аватар. Попробуйте позже";
            case DELETE -> "Не удалось удалить аватар. Попробуйте позже";
            case LOAD -> "Не удалось загрузить аватар. Попробуйте позже";
        };
        log.info("Avatar op failed - server error op={}", ex.op());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", msg));
    }

    // если размер превышен на уровне multipart до попадания в контроллер
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Map<String, String>> handleMultipartTooLarge(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Размер файла не должен превышать 5 МБ"));
    }
}
