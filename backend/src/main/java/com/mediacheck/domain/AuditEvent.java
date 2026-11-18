package com.mediacheck.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends TenantOwnedEntity {
  @Column(name = "actor_user_id")
  private UUID actorUserId;
  @Column(name = "actor_membership_id")
  private UUID actorMembershipId;
  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;
  @Column(name = "subject_type", nullable = false, length = 100)
  private String subjectType;
  @Column(name = "subject_id")
  private UUID subjectId;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson;
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {
  }

  public AuditEvent(Company company, String eventType, String subjectType, UUID subjectId, String metadataJson,
      Instant occurredAt) {
    super(company);
    this.eventType = eventType;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.metadataJson = metadataJson;
    this.occurredAt = occurredAt;
  }

  public String getEventType() {
    return eventType;
  }

  public String getSubjectType() {
    return subjectType;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setActor(UUID userId, UUID membershipId) {
    this.actorUserId = userId;
    this.actorMembershipId = membershipId;
  }
}
