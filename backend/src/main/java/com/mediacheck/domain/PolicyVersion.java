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
@Table(name = "policy_versions")
public class PolicyVersion extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private CompanyPolicy policy;
  @Column(name = "version_number", nullable = false)
  private Integer versionNumber;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private RecordStatus status;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
  private String rulesJson;
  @Column(name = "published_at")
  private Instant publishedAt;
}
