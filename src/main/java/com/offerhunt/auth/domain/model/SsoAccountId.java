package com.offerhunt.auth.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Композитный идентификатор SSO-привязки: (provider, provider_user_id).
 */
public class SsoAccountId implements Serializable {

    private String provider;
    private String providerUserId;

    public SsoAccountId() {
    }

    public SsoAccountId(String provider, String providerUserId) {
        this.provider = provider;
        this.providerUserId = providerUserId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SsoAccountId that)) return false;
        return Objects.equals(provider, that.provider)
            && Objects.equals(providerUserId, that.providerUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providerUserId);
    }
}
