package com.mediacheck.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.mediacheck.domain.AccountActionToken;
public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken,UUID> {
  Optional<AccountActionToken> findByTokenHashAndPurpose(String tokenHash,AccountActionToken.Purpose purpose);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from AccountActionToken t where t.tokenHash = :tokenHash and t.purpose = :purpose")
  Optional<AccountActionToken> findByTokenHashAndPurposeForUpdate(@Param("tokenHash") String tokenHash,@Param("purpose") AccountActionToken.Purpose purpose);
  void deleteAllByUserIdAndPurpose(UUID userId,AccountActionToken.Purpose purpose);
}
