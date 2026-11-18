package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
  @Column(nullable = false, length = 200)
  private String name;
  @Column(name = "legal_name", length = 200)
  private String legalName;
  @Column(name = "business_email", length = 320)
  private String businessEmail;
  @Column(name = "contact_number", length = 40)
  private String contactNumber;
  @Column(length = 512)
  private String website;
  @Column(nullable = false, unique = true, length = 100)
  private String slug;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private CompanyStatus status;

  protected Company() {
  }

  public Company(String name, String slug) {
    this.name = name;
    this.slug = slug;
    this.status = CompanyStatus.ACTIVE;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public void setRegistrationDetails(String legalName, String businessEmail, String contactNumber, String website) {
    this.legalName = legalName;
    this.businessEmail = businessEmail;
    this.contactNumber = contactNumber;
    this.website = website;
  }
  public void updateName(String name) { this.name = name; }

  public String getLegalName() { return legalName; }
  public String getBusinessEmail() { return businessEmail; }
  public String getContactNumber() { return contactNumber; }
  public String getWebsite() { return website; }
  public CompanyStatus getStatus() { return status; }
}
