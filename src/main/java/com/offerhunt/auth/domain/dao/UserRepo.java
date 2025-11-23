package com.offerhunt.auth.domain.dao;

import com.offerhunt.auth.domain.model.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}
