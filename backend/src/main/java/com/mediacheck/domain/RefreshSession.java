package com.mediacheck.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession extends BaseEntity {
  @Column(name="user_id", nullable=false) private UUID userId;
  @Column(name="company_id", nullable=false) private UUID companyId;
  @Column(name="membership_id", nullable=false) private UUID membershipId;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name="token_hash", nullable=false, unique=true, length=64, columnDefinition="char(64)") private String tokenHash;
  @Column(name="issued_at", nullable=false) private Instant issuedAt;
  @Column(name="expires_at", nullable=false) private Instant expiresAt;
  @Column(name="revoked_at") private Instant revokedAt;
  @Column(name="replaced_by") private UUID replacedBy;
  protected RefreshSession() {}
  public RefreshSession(UUID userId, UUID companyId, UUID membershipId, String tokenHash, Instant issuedAt, Instant expiresAt) {
    this.userId=userId; this.companyId=companyId; this.membershipId=membershipId; this.tokenHash=tokenHash; this.issuedAt=issuedAt; this.expiresAt=expiresAt;
  }
  public UUID getUserId(){return userId;} public UUID getCompanyId(){return companyId;} public UUID getMembershipId(){return membershipId;}
  public String getTokenHash(){return tokenHash;} public Instant getExpiresAt(){return expiresAt;} public Instant getRevokedAt(){return revokedAt;}
  public UUID getReplacedBy(){return replacedBy;} public void revoke(Instant now, UUID replacement){revokedAt=now; replacedBy=replacement;}
}
