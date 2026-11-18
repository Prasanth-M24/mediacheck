package com.mediacheck.repository;

import com.mediacheck.domain.LicenceRecord;
import com.mediacheck.domain.RecordStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LicenceRecordRepository extends JpaRepository<LicenceRecord, UUID> {
  Optional<LicenceRecord> findByCompany_IdAndId(UUID companyId, UUID id);
  Page<LicenceRecord> findByCompany_Id(UUID companyId, Pageable pageable);

  @Query(value = "SELECT * FROM licence_records WHERE company_id = :companyId " +
      "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
      "AND (CAST(:search AS TEXT) IS NULL OR LOWER(COALESCE(name, '')) LIKE LOWER('%' || CAST(:search AS TEXT) || '%') OR LOWER(licence_reference) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
      countQuery = "SELECT COUNT(*) FROM licence_records WHERE company_id = :companyId " +
      "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
      "AND (CAST(:search AS TEXT) IS NULL OR LOWER(COALESCE(name, '')) LIKE LOWER('%' || CAST(:search AS TEXT) || '%') OR LOWER(licence_reference) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
      nativeQuery = true)
  Page<LicenceRecord> findByCompanyFiltered(@Param("companyId") UUID companyId, @Param("status") String status,
      @Param("search") String search, Pageable pageable);

  long countByCompany_Id(UUID companyId);
  long countByCompany_IdAndStatus(UUID companyId, RecordStatus status);
  long countByCompany_IdAndStatusAndValidUntilBefore(UUID companyId, RecordStatus status, Instant before);

  @Query("SELECT COUNT(l) FROM LicenceRecord l WHERE l.company.id = :companyId AND l.status = 'ACTIVE' AND l.validUntil IS NOT NULL AND l.validUntil <= :threshold")
  long countExpiringSoon(@Param("companyId") UUID companyId, @Param("threshold") Instant threshold);
  List<LicenceRecord> findTop5ByCompany_IdOrderByCreatedAtDesc(UUID companyId);
}
