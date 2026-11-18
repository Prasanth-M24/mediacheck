package com.mediacheck.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.mediacheck.domain.PlatformUser;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
  Optional<PlatformUser> findByEmail(String email);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from PlatformUser u where u.email = :email")
  Optional<PlatformUser> findByEmailForLogin(@Param("email") String email);
  boolean existsByEmail(String email);
}
