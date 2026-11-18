package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class PlatformUser extends BaseEntity {
  @Column(nullable = false, unique = true, length = 320)
  private String email;
  @Column(nullable = false, length = 200)
  private String displayName;
  @Column(name = "password_hash", length = 100)
  private String passwordHash;
  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;
  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;
  @Column(name = "locked_until")
  private java.time.Instant lockedUntil;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private UserStatus status;

  protected PlatformUser() {
  }

  public PlatformUser(String email, String displayName) {
    this.email = email;
    this.displayName = displayName;
    this.status = UserStatus.ACTIVE;
  }

  public PlatformUser(String email, String displayName, String passwordHash, UserStatus status) {
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.status = status;
    this.emailVerified = status == UserStatus.ACTIVE;
  }

  public String getEmail() {
    return email;
  }
  public String getDisplayName() { return displayName; }
  public String getPasswordHash() { return passwordHash; }
  public UserStatus getStatus() { return status; }
  public boolean isEmailVerified() { return emailVerified; }
  public int getFailedLoginAttempts() { return failedLoginAttempts; }
  public java.time.Instant getLockedUntil() { return lockedUntil; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public void setStatus(UserStatus status) { this.status = status; }
  public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
  public void recordFailedLogin(int maxAttempts, java.time.Instant lockUntil) {
    failedLoginAttempts++;
    if (failedLoginAttempts >= maxAttempts) { status = UserStatus.SUSPENDED; lockedUntil = lockUntil; }
  }
  public void clearFailedLogins() { failedLoginAttempts = 0; lockedUntil = null; }
  public void setLockedUntil(java.time.Instant lockedUntil) { this.lockedUntil = lockedUntil; }
  public void expireLoginLock(java.time.Instant now) {
    if (status == UserStatus.SUSPENDED && failedLoginAttempts >= 5 && lockedUntil != null && !lockedUntil.isAfter(now) && emailVerified) {
      status = UserStatus.ACTIVE;
      clearFailedLogins();
    }
  }
  public void unlockAfterPasswordReset() { if (status == UserStatus.SUSPENDED && failedLoginAttempts >= 5 && lockedUntil != null && emailVerified) status = UserStatus.ACTIVE; clearFailedLogins(); }
}
