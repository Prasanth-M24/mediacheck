package com.mediacheck.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "licence_records")
public class LicenceRecord extends TenantOwnedEntity {
  @Column(name = "licence_reference", nullable = false, length = 200)
  private String licenceReference;

  @Column(length = 300)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "licence_type", length = 64)
  private String licenceType;

  @Column(name = "licensor_name", nullable = false, length = 300)
  private String licensorName;

  @Column(name = "licensee_name", length = 300)
  private String licenseeName;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private RecordStatus status = RecordStatus.ACTIVE;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "terms_json", columnDefinition = "jsonb")
  private String termsJson;

  @Column(name = "created_by_membership_id")
  private UUID createdByMembershipId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @ManyToMany
  @JoinTable(name = "licence_rights_assets",
      joinColumns = @JoinColumn(name = "licence_record_id"),
      inverseJoinColumns = @JoinColumn(name = "rights_asset_id"))
  private Set<RightsAsset> rightsAssets = new LinkedHashSet<>();

  protected LicenceRecord() {
  }

  public LicenceRecord(Company company, String licenceReference, String name, String licenceType,
      String licensorName, String licenseeName, Instant validFrom, Instant validUntil,
      RecordStatus status, String termsJson) {
    super(company);
    this.licenceReference = licenceReference;
    this.name = name;
    this.licenceType = licenceType;
    this.licensorName = licensorName;
    this.licenseeName = licenseeName;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.status = status != null ? status : RecordStatus.ACTIVE;
    this.termsJson = termsJson;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PrePersist
  public void onPrePersist() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
    if (status == null) status = RecordStatus.ACTIVE;
  }

  @PreUpdate
  public void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public String getLicenceReference() {
    return licenceReference;
  }

  public void setLicenceReference(String licenceReference) {
    this.licenceReference = licenceReference;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLicenceType() {
    return licenceType;
  }

  public void setLicenceType(String licenceType) {
    this.licenceType = licenceType;
  }

  public String getLicensorName() {
    return licensorName;
  }

  public void setLicensorName(String licensorName) {
    this.licensorName = licensorName;
  }

  public String getLicenseeName() {
    return licenseeName;
  }

  public void setLicenseeName(String licenseeName) {
    this.licenseeName = licenseeName;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant validFrom) {
    this.validFrom = validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(Instant validUntil) {
    this.validUntil = validUntil;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public String getTermsJson() {
    return termsJson;
  }

  public void setTermsJson(String termsJson) {
    this.termsJson = termsJson;
  }

  public UUID getCreatedByMembershipId() {
    return createdByMembershipId;
  }

  public void setCreatedByMembershipId(UUID createdByMembershipId) {
    this.createdByMembershipId = createdByMembershipId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Set<RightsAsset> getRightsAssets() {
    return rightsAssets;
  }

  public void setRightsAssets(Set<RightsAsset> rightsAssets) {
    this.rightsAssets = rightsAssets;
  }
}
