package com.mediacheck.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "media_assets")
public class MediaAsset extends TenantOwnedEntity {
  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;
  @Column(name = "uploaded_by_membership_id", nullable = false)
  private UUID uploadedByMembershipId;
  @Column(name = "original_filename", nullable = false, length = 512)
  private String originalFilename;
  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;
  @Column(name = "storage_provider", nullable = false, length = 64)
  private String storageProvider;
  @Column(name = "mime_type", nullable = false, length = 127)
  private String mimeType;
  @Column(name = "file_size_bytes", nullable = false)
  private Long fileSizeBytes;
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "sha256_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
  private String sha256Checksum;
  @Column(name = "width_pixels")
  private Integer widthPixels;
  @Column(name = "height_pixels")
  private Integer heightPixels;
  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_status", nullable = false, length = 32)
  private MediaLifecycleStatus lifecycleStatus;
  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;
}
