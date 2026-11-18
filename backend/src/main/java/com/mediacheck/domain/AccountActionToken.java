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
@Table(name="account_action_tokens")
public class AccountActionToken extends BaseEntity {
  public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }
  @Column(name="user_id",nullable=false) private UUID userId;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name="token_hash",nullable=false,unique=true,length=64,columnDefinition="char(64)") private String tokenHash;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Purpose purpose;
  @Column(name="created_at",nullable=false) private Instant createdAt;
  @Column(name="expires_at",nullable=false) private Instant expiresAt;
  @Column(name="used_at") private Instant usedAt;
  protected AccountActionToken() {}
  public AccountActionToken(UUID userId,String tokenHash,Purpose purpose,Instant createdAt,Instant expiresAt){this.userId=userId;this.tokenHash=tokenHash;this.purpose=purpose;this.createdAt=createdAt;this.expiresAt=expiresAt;}
  public UUID getUserId(){return userId;} public String getTokenHash(){return tokenHash;} public Purpose getPurpose(){return purpose;} public Instant getExpiresAt(){return expiresAt;} public Instant getUsedAt(){return usedAt;} public void use(Instant now){usedAt=now;}
}
