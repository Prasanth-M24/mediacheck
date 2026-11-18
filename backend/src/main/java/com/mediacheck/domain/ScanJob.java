package com.mediacheck.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "scan_jobs")
public class ScanJob extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "media_asset_id", nullable = false)
  private MediaAsset mediaAsset;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private ScanJobStatus status;
  @Column(name = "retry_count", nullable = false)
  private int retryCount;
  @Column(name = "error_details")
  private String errorDetails;
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @Column(name = "started_at")
  private Instant startedAt;
  @Column(name = "completed_at")
  private Instant completedAt;
}
