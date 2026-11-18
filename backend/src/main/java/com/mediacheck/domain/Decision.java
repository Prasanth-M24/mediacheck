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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "decisions")
public class Decision extends TenantOwnedEntity {
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scan_job_id", nullable = false, unique = true)
  private ScanJob scanJob;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_version_id", nullable = false)
  private PolicyVersion policyVersion;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private PolicyDecision decision;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fired_rule_ids", nullable = false, columnDefinition = "jsonb")
  private String firedRuleIds;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "finding_references", nullable = false, columnDefinition = "jsonb")
  private String findingReferences;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String reasons;
  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;
}
