package com.offerhunt.auth.domain.dao;

import com.offerhunt.auth.domain.model.SsoAccount;
import com.offerhunt.auth.domain.model.SsoAccountId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SsoAccountRepo extends JpaRepository<SsoAccount, SsoAccountId> {

    Optional<SsoAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<SsoAccount> findByUser_Id(UUID userId);

    default List<SsoAccount> findByUserId(UUID userId) {
        return findByUser_Id(userId);
    }
}
