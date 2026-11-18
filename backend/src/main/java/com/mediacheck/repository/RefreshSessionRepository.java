package com.mediacheck.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.mediacheck.domain.RefreshSession;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
  Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
  Optional<RefreshSession> findByTokenHash(String tokenHash);
  @Lock(LockModeType.PESSIMISTIC_WRITE) List<RefreshSession> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
