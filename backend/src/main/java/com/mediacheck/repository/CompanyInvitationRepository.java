package com.mediacheck.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.mediacheck.domain.CompanyInvitation;
public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation,UUID> {
  Optional<CompanyInvitation> findByTokenHash(String tokenHash);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from CompanyInvitation i where i.tokenHash = :tokenHash")
  Optional<CompanyInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
  List<CompanyInvitation> findAllByCompanyIdAndAcceptedAtIsNullAndRevokedAtIsNull(UUID companyId);
  Optional<CompanyInvitation> findByCompanyIdAndIdAndRevokedAtIsNullAndAcceptedAtIsNull(UUID companyId,UUID id);
}
