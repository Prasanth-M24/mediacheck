package com.mediacheck.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "evidence_records")
public class EvidenceRecord extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pipeline_run_id", nullable = false)
  private PipelineRun pipelineRun;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_id")
  private Finding finding;
  @Column(name = "evidence_type", nullable = false, length = 100)
  private String evidenceType;
  @Column(name = "storage_key", length = 512)
  private String storageKey;
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 64, columnDefinition = "char(64)")
  private String checksum;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details_json", columnDefinition = "jsonb")
  private String detailsJson;
}
