package com.offerhunt.auth.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "auth_user_sso_accounts", schema = "auth")
@IdClass(SsoAccountId.class)
public class SsoAccount {

    @Id
    @Column(name = "provider", nullable = false)
    private String provider;

    @Id
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "email_at_provider")
    private String emailAtProvider;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected SsoAccount() {
    }

    public SsoAccount(
        String provider,
        String providerUserId,
        UserEntity user,
        String emailAtProvider,
        Boolean emailVerified,
        Instant linkedAt,
        Instant lastLoginAt
    ) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.user = user;
        this.emailAtProvider = emailAtProvider;
        this.emailVerified = emailVerified;
        this.linkedAt = linkedAt;
        this.lastLoginAt = lastLoginAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getEmailAtProvider() {
        return emailAtProvider;
    }

    public void setEmailAtProvider(String emailAtProvider) {
        this.emailAtProvider = emailAtProvider;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
