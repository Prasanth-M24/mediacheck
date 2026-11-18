package com.mediacheck.domain;

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
@Table(name = "check_results")
public class CheckResult extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pipeline_run_id", nullable = false)
  private PipelineRun pipelineRun;
  @Column(name = "check_name", nullable = false, length = 200)
  private String checkName;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private EvidenceStatus status;
  @Column(nullable = false)
  private String summary;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details_json", columnDefinition = "jsonb")
  private String detailsJson;
}
