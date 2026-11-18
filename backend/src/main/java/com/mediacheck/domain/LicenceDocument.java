package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "licence_documents")
public class LicenceDocument extends TenantOwnedEntity {

  @Column(name = "created_at", nullable = false, updatable = false)
  private java.time.Instant createdAt = java.time.Instant.now();

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "uploaded_by_membership_id")
  private java.util.UUID uploadedByMembershipId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "licence_record_id", nullable = false)
  private LicenceRecord licenceRecord;
  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;
  @Column(name = "original_filename", nullable = false, length = 512)
  private String originalFilename;
  @Column(name = "mime_type", nullable = false, length = 127)
  private String mimeType;
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "sha256_checksum", length = 64, columnDefinition = "char(64)")
  private String sha256Checksum;

  protected LicenceDocument() {
  }

  public LicenceDocument(Company company, LicenceRecord licenceRecord, String storageKey,
      String originalFilename, String mimeType, Long fileSize, String sha256Checksum) {
    super(company);
    this.licenceRecord = licenceRecord;
    this.storageKey = storageKey;
    this.originalFilename = originalFilename;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
    this.sha256Checksum = sha256Checksum;
  }

  public LicenceRecord getLicenceRecord() { return licenceRecord; }
  public void setLicenceRecord(LicenceRecord licenceRecord) { this.licenceRecord = licenceRecord; }
  public String getStorageKey() { return storageKey; }
  public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
  public String getOriginalFilename() { return originalFilename; }
  public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
  public String getMimeType() { return mimeType; }
  public void setMimeType(String mimeType) { this.mimeType = mimeType; }
  public Long getFileSize() { return fileSize; }
  public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
  public java.util.UUID getUploadedByMembershipId() { return uploadedByMembershipId; }
  public void setUploadedByMembershipId(java.util.UUID uploadedByMembershipId) { this.uploadedByMembershipId = uploadedByMembershipId; }
  public String getSha256Checksum() { return sha256Checksum; }
  public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
  public java.time.Instant getCreatedAt() { return createdAt; }
}
