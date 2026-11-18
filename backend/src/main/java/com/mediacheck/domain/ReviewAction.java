package com.mediacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_actions")
public class ReviewAction extends TenantOwnedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scan_job_id", nullable = false)
  private ScanJob scanJob;
  @Column(name = "actor_user_id", nullable = false)
  private java.util.UUID actorUserId;
  @Column(name = "actor_membership_id", nullable = false)
  private java.util.UUID actorMembershipId;
  @Column(name = "action_type", nullable = false, length = 64)
  private String actionType;
  @Column(name = "note")
  private String note;
}
