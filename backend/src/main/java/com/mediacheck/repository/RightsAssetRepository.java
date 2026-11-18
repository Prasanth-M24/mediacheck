package com.mediacheck.repository;

import com.mediacheck.domain.RecordStatus;
import com.mediacheck.domain.RightsAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RightsAssetRepository extends JpaRepository<RightsAsset, UUID> {
  Optional<RightsAsset> findByCompany_IdAndId(UUID companyId, UUID id);

  Page<RightsAsset> findByCompany_Id(UUID companyId, Pageable pageable);

  @Query(value = "SELECT * FROM rights_assets WHERE company_id = :companyId " +
      "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
      "AND (CAST(:assetType AS TEXT) IS NULL OR asset_type = CAST(:assetType AS TEXT)) " +
      "AND (CAST(:search AS TEXT) IS NULL OR LOWER(title) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
      countQuery = "SELECT COUNT(*) FROM rights_assets WHERE company_id = :companyId " +
      "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
      "AND (CAST(:assetType AS TEXT) IS NULL OR asset_type = CAST(:assetType AS TEXT)) " +
      "AND (CAST(:search AS TEXT) IS NULL OR LOWER(title) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
      nativeQuery = true)
  Page<RightsAsset> findByCompanyFiltered(@Param("companyId") UUID companyId, @Param("status") String status,
      @Param("assetType") String assetType, @Param("search") String search, Pageable pageable);

  long countByCompany_Id(UUID companyId);
  long countByCompany_IdAndStatus(UUID companyId, RecordStatus status);
  List<RightsAsset> findTop5ByCompany_IdOrderByCreatedAtDesc(UUID companyId);
}
