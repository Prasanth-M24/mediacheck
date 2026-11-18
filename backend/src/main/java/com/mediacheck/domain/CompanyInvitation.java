package com.mediacheck.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="company_invitations")
public class CompanyInvitation extends BaseEntity {
  @Column(name="company_id",nullable=false) private UUID companyId;
  @Column(name="invited_by_membership_id",nullable=false) private UUID invitedByMembershipId;
  @Column(nullable=false,length=320) private String email;
  @Enumerated(EnumType.STRING) @Column(name="membership_role",nullable=false,length=32) private MembershipRole membershipRole;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name="token_hash",nullable=false,unique=true,length=64,columnDefinition="char(64)") private String tokenHash;
  @Column(name="created_at",nullable=false) private Instant createdAt;
  @Column(name="expires_at",nullable=false) private Instant expiresAt;
  @Column(name="accepted_at") private Instant acceptedAt;
  @Column(name="revoked_at") private Instant revokedAt;
  protected CompanyInvitation() {}
  public CompanyInvitation(UUID companyId,UUID invitedByMembershipId,String email,MembershipRole role,String tokenHash,Instant createdAt,Instant expiresAt){
    this.companyId=companyId;this.invitedByMembershipId=invitedByMembershipId;this.email=email;this.membershipRole=role;this.tokenHash=tokenHash;this.createdAt=createdAt;this.expiresAt=expiresAt;
  }
  public UUID getCompanyId(){return companyId;} public UUID getInvitedByMembershipId(){return invitedByMembershipId;} public String getEmail(){return email;}
  public MembershipRole getMembershipRole(){return membershipRole;} public String getTokenHash(){return tokenHash;} public Instant getExpiresAt(){return expiresAt;}
  public Instant getAcceptedAt(){return acceptedAt;} public Instant getRevokedAt(){return revokedAt;} public void accept(Instant now){acceptedAt=now;} public void revoke(Instant now){revokedAt=now;}
}
