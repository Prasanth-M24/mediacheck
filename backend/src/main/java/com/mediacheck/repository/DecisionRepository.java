package com.mediacheck.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.Decision;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
  Optional<Decision> findByCompany_IdAndId(UUID companyId, UUID id);

  Optional<Decision> findByCompany_IdAndScanJob_Id(UUID companyId, UUID scanJobId);
}
