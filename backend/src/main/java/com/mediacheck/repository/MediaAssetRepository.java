package com.mediacheck.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.MediaAsset;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
  Optional<MediaAsset> findByCompany_IdAndId(UUID companyId, UUID id);

  List<MediaAsset> findAllByCompany_IdOrderByUploadedAtDesc(UUID companyId);
}
