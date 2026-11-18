package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "company_memberships", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "company_id", "user_id" })
})
public class CompanyMembership extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private PlatformUser user;
  @Enumerated(EnumType.STRING)
  @Column(name = "membership_role", nullable = false, length = 32, columnDefinition = "varchar(32)")
  private MembershipRole membershipRole;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private MembershipStatus status;

  protected CompanyMembership() {
  }

  public CompanyMembership(Company company, PlatformUser user, MembershipRole role) {
    this.company = company;
    this.user = user;
    this.membershipRole = role;
    this.status = MembershipStatus.ACTIVE;
  }

  public Company getCompany() { return company; }
  public PlatformUser getUser() { return user; }
  public MembershipRole getMembershipRole() { return membershipRole; }
  public MembershipStatus getStatus() { return status; }
  public void setMembershipRole(MembershipRole role) { this.membershipRole = role; }
  public void setStatus(MembershipStatus status) { this.status = status; }
}
