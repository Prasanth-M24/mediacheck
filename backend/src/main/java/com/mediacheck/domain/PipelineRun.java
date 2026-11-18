package com.mediacheck.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_runs")
public class PipelineRun extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scan_job_id", nullable = false)
  private ScanJob scanJob;
  @Enumerated(EnumType.STRING)
  @Column(name = "pipeline_type", nullable = false, length = 64, columnDefinition = "varchar(64)")
  private PipelineType pipelineType;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private PipelineStatus status;
  @Column(name = "pipeline_version", length = 100)
  private String pipelineVersion;
  @Column(name = "started_at")
  private Instant startedAt;
  @Column(name = "completed_at")
  private Instant completedAt;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details_json", columnDefinition = "jsonb")
  private String detailsJson;
}
