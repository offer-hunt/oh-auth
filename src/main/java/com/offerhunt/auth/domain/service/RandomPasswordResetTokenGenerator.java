package com.offerhunt.auth.domain.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class RandomPasswordResetTokenGenerator implements PasswordResetTokenGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
