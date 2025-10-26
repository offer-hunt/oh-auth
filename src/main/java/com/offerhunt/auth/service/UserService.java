package com.offerhunt.auth.service;

import java.util.UUID;

import com.offerhunt.auth.dao.UserRepo;
import com.offerhunt.auth.model.UserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepo repo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepo repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    public UserEntity createLocal(String email, String passHash, String name) {
        return repo.save(new UserEntity(UUID.randomUUID(), email.toLowerCase(), passHash, name));
    }

    public UserEntity verifyCredentials(String email, String rawPassword) {
        UserEntity u = repo.findByEmail(email.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("bad credentials");
        }
        return u;
    }
}
