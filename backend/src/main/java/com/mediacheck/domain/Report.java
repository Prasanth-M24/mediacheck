package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class Report extends TenantOwnedEntity {
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "decision_id", nullable = false, unique = true)
  private Decision decision;
  @Column(name = "storage_key", length = 512)
  private String storageKey;
  @Enumerated(EnumType.STRING)
  @Column(name = "report_status", nullable = false, length = 32, columnDefinition = "varchar(32)")
  private ReportStatus reportStatus;
}
