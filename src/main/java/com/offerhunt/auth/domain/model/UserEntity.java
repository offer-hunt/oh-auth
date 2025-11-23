package com.offerhunt.auth.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_users", schema = "auth")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, name = "global_role")
    private String globalRole = "USER";

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected UserEntity() {
    }

    public UserEntity(UUID id, String email, String passwordHash, String fullName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGlobalRole() {
        return globalRole;
    }
}
