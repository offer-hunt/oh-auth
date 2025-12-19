package com.offerhunt.auth.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class LoggingPasswordResetMailService implements PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailService.class);

    @Override
    public void sendResetLink(String email, String url) {
        log.info("Password reset email to {}: {}", email, url);
    }
}
