package com.mediacheck.media;

import com.mediacheck.domain.PolicyDecision;
import com.mediacheck.domain.EvidenceStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Versioned Deterministic Decision Authority.
 *
 * Decision Matrix (v phase3-2.0):
 * 1. If ANY finding has status FAIL -> BLOCK
 * 2. Else if ANY finding has status ADVISORY_FLAG, UNAVAILABLE, or UNKNOWN -> REVIEW
 * 3. Else (all findings are PASS or NOT_APPLICABLE) -> APPROVE
 *
 * CRITICAL ARCHITECTURAL GUARANTEES:
 * - This engine is the SOLE authority for the final review decision.
 * - AI explanation providers NEVER participate in or alter this decision.
 * - Score averaging is strictly prohibited.
 * - Missing or unavailable mandatory checks cannot silently pass.
 */
public final class DeterministicDecisionEngine {
  public static final String VERSION = "phase3-2.0";

  private DeterministicDecisionEngine() {}

  public record DecisionResult(
      PolicyDecision decision,
      List<String> reasons
  ) {}

  public static DecisionResult decideWithReasons(List<EvidenceStatus> statuses) {
    List<String> reasons = new ArrayList<>();
    reasons.add("Deterministic authority version " + VERSION);

    if (statuses == null || statuses.isEmpty()) {
      reasons.add("No analysis evidence provided; defaulting to REVIEW.");
      return new DecisionResult(PolicyDecision.REVIEW, reasons);
    }

    boolean hasFail = statuses.stream().anyMatch(EvidenceStatus.FAIL::equals);
    boolean hasReviewRequired = statuses.stream().anyMatch(s ->
        s == EvidenceStatus.ADVISORY_FLAG || s == EvidenceStatus.UNAVAILABLE || s == EvidenceStatus.UNKNOWN);

    if (hasFail) {
      reasons.add("One or more critical checks failed mandatory requirements.");
      return new DecisionResult(PolicyDecision.BLOCK, reasons);
    } else if (hasReviewRequired) {
      reasons.add("One or more checks produced advisory flags, unverified status, or require human reviewer sign-off.");
      return new DecisionResult(PolicyDecision.REVIEW, reasons);
    } else {
      reasons.add("All verification checks passed without conflict under active policies.");
      return new DecisionResult(PolicyDecision.APPROVE, reasons);
    }
  }

  public static PolicyDecision decide(List<EvidenceStatus> statuses) {
    return decideWithReasons(statuses).decision();
  }
}
