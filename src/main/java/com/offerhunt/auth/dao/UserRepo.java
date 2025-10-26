package com.offerhunt.auth.dao;

import java.util.Optional;
import java.util.UUID;

import com.offerhunt.auth.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);
}
