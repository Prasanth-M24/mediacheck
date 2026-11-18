package com.mediacheck.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
  List<AuditEvent> findAllByCompany_IdOrderByOccurredAtDesc(UUID companyId);
}
