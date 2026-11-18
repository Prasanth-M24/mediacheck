package com.mediacheck.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.ScanJob;
import com.mediacheck.domain.ScanJobStatus;

public interface ScanJobRepository extends JpaRepository<ScanJob, UUID> {
  Optional<ScanJob> findByCompany_IdAndId(UUID companyId, UUID id);

  List<ScanJob> findAllByCompany_IdAndStatusOrderByCreatedAtDesc(UUID companyId, ScanJobStatus status);
}
