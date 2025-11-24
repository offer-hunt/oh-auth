package com.offerhunt.auth.domain.service;

public interface PasswordResetMailService {

    void sendResetLink(String email, String url);
}
