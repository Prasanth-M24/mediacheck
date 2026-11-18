package com.mediacheck.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rights_assets")
public class RightsAsset extends TenantOwnedEntity {
  @Column(nullable = false, length = 300)
  private String title;

  @Column(name = "asset_type", nullable = false, length = 64)
  private String assetType;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "external_reference", length = 200)
  private String externalReference;

  @Column(name = "rights_holder", length = 300)
  private String rightsHolder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private RecordStatus status = RecordStatus.ACTIVE;

  @Column(name = "original_filename", length = 512)
  private String originalFilename;

  @Column(name = "mime_type", length = 127)
  private String mimeType;

  @Column(name = "file_size")
  private Long fileSize;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "sha256_checksum", length = 64, columnDefinition = "char(64)")
  private String sha256Checksum;

  @Column(name = "storage_key", length = 512)
  private String storageKey;

  @Column(name = "created_by_membership_id")
  private UUID createdByMembershipId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected RightsAsset() {
  }

  public RightsAsset(Company company, String title, String assetType, String description,
      String externalReference, String rightsHolder, RecordStatus status) {
    super(company);
    this.title = title;
    this.assetType = assetType;
    this.description = description;
    this.externalReference = externalReference;
    this.rightsHolder = rightsHolder;
    this.status = status != null ? status : RecordStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PrePersist
  public void onPrePersist() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
    if (status == null) status = RecordStatus.ACTIVE;
  }

  @PreUpdate
  public void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAssetType() {
    return assetType;
  }

  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public void setExternalReference(String externalReference) {
    this.externalReference = externalReference;
  }

  public String getRightsHolder() {
    return rightsHolder;
  }

  public void setRightsHolder(String rightsHolder) {
    this.rightsHolder = rightsHolder;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getSha256Checksum() {
    return sha256Checksum;
  }

  public void setSha256Checksum(String sha256Checksum) {
    this.sha256Checksum = sha256Checksum;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public UUID getCreatedByMembershipId() {
    return createdByMembershipId;
  }

  public void setCreatedByMembershipId(UUID createdByMembershipId) {
    this.createdByMembershipId = createdByMembershipId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
