package com.mediacheck.registry;

import com.mediacheck.domain.*;
import com.mediacheck.repository.*;
import com.mediacheck.storage.StorageService;
import com.mediacheck.storage.StoredFile;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static com.mediacheck.registry.RegistryDtos.*;

@Service
public class RightsRegistryService {
  private final CompanyRepository companies;
  private final RightsAssetRepository assets;
  private final LicenceRecordRepository licences;
  private final LicenceDocumentRepository documents;
  private final AuditEventRepository audit;
  private final StorageService storage;

  public RightsRegistryService(CompanyRepository companies, RightsAssetRepository assets,
      LicenceRecordRepository licences, LicenceDocumentRepository documents,
      AuditEventRepository audit, StorageService storage) {
    this.companies = companies; this.assets = assets; this.licences = licences;
    this.documents = documents; this.audit = audit; this.storage = storage;
  }

  @Transactional
  public AssetResponse createAsset(UUID companyId, UUID userId, UUID membershipId, CreateAssetRequest r) {
    Company c = company(companyId);
    RightsAsset a = assets.save(new RightsAsset(c, r.title().trim(), r.assetType().trim(), r.description(),
        blank(r.externalReference()), blank(r.rightsHolder()), RecordStatus.ACTIVE));
    a.setCreatedByMembershipId(membershipId); audit(c, userId, membershipId, "RIGHTS_ASSET_CREATED", a.getId());
    return asset(a);
  }

  @Transactional(readOnly = true)
  public PagedResponse<AssetResponse> listAssets(UUID companyId, String status, String type, String search, Pageable pageable) {
    RecordStatus s = status == null || status.isBlank() ? null : enumValue(status, RecordStatus.class);
    Page<RightsAsset> page = assets.findByCompanyFiltered(companyId, s == null ? null : s.name(), blank(type), blank(search), pageable);
    return new PagedResponse<>(page.getContent().stream().map(this::asset).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AssetResponse getAsset(UUID companyId, UUID id) { return asset(asset(companyId, id)); }

  @Transactional
  public AssetResponse updateAsset(UUID companyId, UUID userId, UUID membershipId, UUID id, UpdateAssetRequest r) {
    RightsAsset a = asset(companyId, id); a.setTitle(r.title().trim()); a.setAssetType(r.assetType().trim());
    a.setDescription(r.description()); a.setExternalReference(blank(r.externalReference())); a.setRightsHolder(blank(r.rightsHolder()));
    audit(a.getCompany(), userId, membershipId, "RIGHTS_ASSET_UPDATED", id); return asset(a);
  }

  @Transactional
  public void archiveAsset(UUID companyId, UUID userId, UUID membershipId, UUID id) {
    RightsAsset a = asset(companyId, id); a.setStatus(RecordStatus.ARCHIVED); audit(a.getCompany(), userId, membershipId, "RIGHTS_ASSET_ARCHIVED", id);
  }

  @Transactional
  public AssetResponse uploadAsset(UUID companyId, UUID userId, UUID membershipId, UUID id, MultipartFile file) {
    RightsAsset a = asset(companyId, id); StoredFile stored = store("rights-assets", companyId, id, file);
    a.setOriginalFilename(safeFilename(file.getOriginalFilename())); a.setMimeType(stored.mimeType()); a.setFileSize(stored.fileSize());
    a.setSha256Checksum(stored.sha256Checksum()); a.setStorageKey(stored.storageKey());
    audit(a.getCompany(), userId, membershipId, "RIGHTS_ASSET_FILE_UPLOADED", id); return asset(a);
  }

  public InputStream assetFile(UUID companyId, UUID id) { return storage.retrieve(asset(companyId, id).getStorageKey()); }

  @Transactional
  public LicenceResponse createLicence(UUID companyId, UUID userId, UUID membershipId, CreateLicenceRequest r) {
    Company c = company(companyId); RecordStatus status = r.status() == null ? RecordStatus.DRAFT : registryStatus(r.status());
    validateDates(r.validFrom(), r.validUntil());
    LicenceRecord l = licences.save(new LicenceRecord(c, r.licenceReference().trim(), blank(r.name()), blank(r.licenceType()),
        r.licensorName().trim(), blank(r.licenseeName()), r.validFrom(), r.validUntil(), status, r.termsJson()));
    l.setCreatedByMembershipId(membershipId); audit(c, userId, membershipId, "LICENCE_CREATED", l.getId()); return licence(l);
  }

  @Transactional(readOnly = true)
  public PagedResponse<LicenceResponse> listLicences(UUID companyId, String status, String search, Pageable pageable) {
    RecordStatus s = status == null || status.isBlank() ? null : registryStatus(status);
    Page<LicenceRecord> page = licences.findByCompanyFiltered(companyId, s == null ? null : s.name(), blank(search), pageable);
    return new PagedResponse<>(page.getContent().stream().map(this::licence).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public LicenceResponse getLicence(UUID companyId, UUID id) { return licence(licenceEntity(companyId, id)); }

  @Transactional
  public LicenceResponse updateLicence(UUID companyId, UUID userId, UUID membershipId, UUID id, UpdateLicenceRequest r) {
    validateDates(r.validFrom(), r.validUntil()); LicenceRecord l = licenceEntity(companyId, id);
    l.setLicenceReference(r.licenceReference().trim()); l.setName(blank(r.name())); l.setDescription(r.description());
    l.setLicenceType(blank(r.licenceType())); l.setLicensorName(r.licensorName().trim()); l.setLicenseeName(blank(r.licenseeName()));
    l.setValidFrom(r.validFrom()); l.setValidUntil(r.validUntil()); l.setTermsJson(r.termsJson());
    audit(l.getCompany(), userId, membershipId, "LICENCE_UPDATED", id); return licence(l);
  }

  @Transactional
  public LicenceResponse changeLicenceStatus(UUID companyId, UUID userId, UUID membershipId, UUID id, String status) {
    LicenceRecord l = licenceEntity(companyId, id); RecordStatus next = registryStatus(status);
    if (next == RecordStatus.RETIRED || next == RecordStatus.INACTIVE) throw bad("unsupported_licence_status");
    l.setStatus(next); audit(l.getCompany(), userId, membershipId, "LICENCE_STATUS_CHANGED", id); return licence(l);
  }

  @Transactional
  public DocumentResponse uploadDocument(UUID companyId, UUID userId, UUID membershipId, UUID licenceId, MultipartFile file) {
    LicenceRecord l = licenceEntity(companyId, licenceId); UUID id = UUID.randomUUID(); StoredFile stored = store("licence-documents", companyId, id, file);
    LicenceDocument d = documents.save(new LicenceDocument(l.getCompany(), l, stored.storageKey(), safeFilename(file.getOriginalFilename()), stored.mimeType(), stored.fileSize(), stored.sha256Checksum()));
    d.setUploadedByMembershipId(membershipId); audit(l.getCompany(), userId, membershipId, "LICENCE_DOCUMENT_UPLOADED", d.getId()); return document(d);
  }

  @Transactional(readOnly = true)
  public List<DocumentResponse> listDocuments(UUID companyId, UUID licenceId) { licenceEntity(companyId, licenceId); return documents.findByCompany_IdAndLicenceRecord_Id(companyId, licenceId).stream().map(this::document).toList(); }
  public InputStream documentFile(UUID companyId, UUID licenceId, UUID documentId) { LicenceDocument d = document(companyId, licenceId, documentId); return storage.retrieve(d.getStorageKey()); }

  @Transactional
  public void deleteDocument(UUID companyId, UUID userId, UUID membershipId, UUID licenceId, UUID documentId) {
    LicenceDocument d = document(companyId, licenceId, documentId); storage.delete(d.getStorageKey()); documents.delete(d); audit(d.getCompany(), userId, membershipId, "LICENCE_DOCUMENT_DELETED", documentId);
  }

  @Transactional(readOnly = true)
  public DashboardResponse dashboard(UUID companyId) {
    Instant now = Instant.now(); return new DashboardResponse(
        assets.countByCompany_Id(companyId), assets.countByCompany_IdAndStatus(companyId, RecordStatus.ACTIVE), assets.countByCompany_IdAndStatus(companyId, RecordStatus.ARCHIVED),
        licences.countByCompany_Id(companyId), licences.countByCompany_IdAndStatus(companyId, RecordStatus.ACTIVE), licences.countByCompany_IdAndStatus(companyId, RecordStatus.EXPIRED), licences.countExpiringSoon(companyId, now.plusSeconds(30L * 86400)),
        licences.countByCompany_IdAndStatus(companyId, RecordStatus.DRAFT), licences.countByCompany_IdAndStatus(companyId, RecordStatus.REVOKED),
        assets.findTop5ByCompany_IdOrderByCreatedAtDesc(companyId).stream().map(a -> new AssetSummary(a.getId(), a.getTitle(), a.getAssetType(), a.getStatus().name(), a.getCreatedAt())).toList(),
        licences.findTop5ByCompany_IdOrderByCreatedAtDesc(companyId).stream().map(l -> new LicenceSummary(l.getId(), l.getName(), l.getLicenceReference(), effective(l).name(), l.getValidUntil(), l.getCreatedAt())).toList());
  }

  private StoredFile store(String category, UUID companyId, UUID id, MultipartFile file) {
    if (file == null || file.isEmpty()) throw bad("empty_file");
    String contentType = file.getContentType();
    if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("audio/") || contentType.startsWith("video/") || contentType.equals("application/pdf") || contentType.equals("text/plain") || contentType.equals("application/msword") || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) throw bad("unsupported_file_type");
    String name = safeFilename(file.getOriginalFilename()); String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "bin";
    try { return storage.store(category, companyId, id, ext, file.getInputStream(), file.getSize()); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file_storage_failed"); }
  }
  private RightsAsset asset(UUID c, UUID id) { return assets.findByCompany_IdAndId(c, id).orElseThrow(() -> notFound("asset_not_found")); }
  private LicenceRecord licenceEntity(UUID c, UUID id) { return licences.findByCompany_IdAndId(c, id).orElseThrow(() -> notFound("licence_not_found")); }
  private LicenceDocument document(UUID c, UUID l, UUID d) { return documents.findByCompany_IdAndLicenceRecord_IdAndId(c, l, d).orElseThrow(() -> notFound("document_not_found")); }
  private Company company(UUID id) { return companies.findById(id).orElseThrow(() -> notFound("company_not_found")); }
  private AssetResponse asset(RightsAsset a) { return new AssetResponse(a.getId(), a.getTitle(), a.getAssetType(), a.getDescription(), a.getExternalReference(), a.getRightsHolder(), a.getStatus().name(), a.getOriginalFilename(), a.getMimeType(), a.getFileSize(), a.getSha256Checksum(), a.getStorageKey() != null, a.getCreatedByMembershipId(), a.getCreatedAt(), a.getUpdatedAt()); }
  private LicenceResponse licence(LicenceRecord l) { return new LicenceResponse(l.getId(), l.getLicenceReference(), l.getName(), l.getDescription(), l.getLicenceType(), l.getLicensorName(), l.getLicenseeName(), l.getValidFrom(), l.getValidUntil(), l.getStatus().name(), effective(l).name(), l.getTermsJson(), l.getRightsAssets().size(), documents.findByCompany_IdAndLicenceRecord_Id(l.getCompany().getId(), l.getId()).size(), l.getCreatedByMembershipId(), l.getCreatedAt(), l.getUpdatedAt()); }
  private DocumentResponse document(LicenceDocument d) { return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getMimeType(), d.getFileSize(), d.getSha256Checksum(), d.getCreatedAt()); }
  private RecordStatus effective(LicenceRecord l) { return l.getStatus() == RecordStatus.ACTIVE && l.getValidUntil() != null && l.getValidUntil().isBefore(Instant.now()) ? RecordStatus.EXPIRED : l.getStatus(); }
  private void audit(Company c, UUID u, UUID m, String type, UUID id) { AuditEvent e = new AuditEvent(c, type, "RIGHTS_REGISTRY", id, "{}", Instant.now()); e.setActor(u, m); audit.save(e); }
  private static String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }
  private static String safeFilename(String v) { if (v == null || v.isBlank()) return "upload"; return v.replace('\\', '/').substring(v.replace('\\', '/').lastIndexOf('/') + 1); }
  private static void validateDates(Instant from, Instant until) { if (from != null && until != null && until.isBefore(from)) throw bad("invalid_date_range"); }
  private static RecordStatus registryStatus(String v) { try { return enumValue(v, RecordStatus.class); } catch (RuntimeException e) { throw bad("invalid_status"); } }
  private static <E extends Enum<E>> E enumValue(String v, Class<E> t) { return Enum.valueOf(t, v.trim().toUpperCase()); }
  private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
  private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
