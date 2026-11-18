package com.mediacheck.domain;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false, updatable = false)
  private Company company;

  protected TenantOwnedEntity() {
  }

  protected TenantOwnedEntity(Company company) {
    this.company = company;
  }

  public Company getCompany() {
    return company;
  }
}
