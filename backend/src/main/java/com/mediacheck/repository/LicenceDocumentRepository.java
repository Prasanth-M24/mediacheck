package com.mediacheck.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediacheck.domain.LicenceDocument;

public interface LicenceDocumentRepository extends JpaRepository<LicenceDocument, UUID> {

    Optional<LicenceDocument> findByCompany_IdAndId(UUID companyId, UUID id);

    List<LicenceDocument> findByCompany_IdAndLicenceRecord_Id(UUID companyId, UUID licenceRecordId);

    Optional<LicenceDocument> findByCompany_IdAndLicenceRecord_IdAndId(UUID companyId, UUID licenceRecordId, UUID id);

    void deleteByCompany_IdAndLicenceRecord_IdAndId(UUID companyId, UUID licenceRecordId, UUID id);
}
