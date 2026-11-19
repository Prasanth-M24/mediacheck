package com.mediacheck.media;

import com.mediacheck.domain.EvidenceStatus;
import com.mediacheck.domain.PolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeterministicDecisionEngineTest {

  @Test
  void approveWhenAllChecksPassOrNotApplicable() {
    var statuses = List.of(EvidenceStatus.PASS, EvidenceStatus.PASS, EvidenceStatus.NOT_APPLICABLE);
    var outcome = DeterministicDecisionEngine.decideWithReasons(statuses);

    assertThat(outcome.decision()).isEqualTo(PolicyDecision.APPROVE);
    assertThat(outcome.reasons()).anyMatch(r -> r.contains("All verification checks passed"));
  }

  @Test
  void reviewWhenAnyAdvisoryFlagPresent() {
    var statuses = List.of(EvidenceStatus.PASS, EvidenceStatus.ADVISORY_FLAG, EvidenceStatus.PASS);
    var outcome = DeterministicDecisionEngine.decideWithReasons(statuses);

    assertThat(outcome.decision()).isEqualTo(PolicyDecision.REVIEW);
    assertThat(outcome.reasons()).anyMatch(r -> r.contains("advisory flags"));
  }

  @Test
  void reviewWhenAnyCheckIsUnavailableOrUnknown() {
    var unavail = List.of(EvidenceStatus.PASS, EvidenceStatus.UNAVAILABLE);
    assertThat(DeterministicDecisionEngine.decide(unavail)).isEqualTo(PolicyDecision.REVIEW);

    var unknown = List.of(EvidenceStatus.PASS, EvidenceStatus.UNKNOWN);
    assertThat(DeterministicDecisionEngine.decide(unknown)).isEqualTo(PolicyDecision.REVIEW);
  }

  @Test
  void blockWhenAnyCheckFailsRegardlessOfOtherPasses() {
    var statuses = List.of(EvidenceStatus.PASS, EvidenceStatus.ADVISORY_FLAG, EvidenceStatus.FAIL, EvidenceStatus.PASS);
    var outcome = DeterministicDecisionEngine.decideWithReasons(statuses);

    assertThat(outcome.decision()).isEqualTo(PolicyDecision.BLOCK);
    assertThat(outcome.reasons()).anyMatch(r -> r.contains("failed mandatory requirements"));
  }

  @Test
  void emptyEvidenceDefaultsToReview() {
    var outcome = DeterministicDecisionEngine.decideWithReasons(List.of());
    assertThat(outcome.decision()).isEqualTo(PolicyDecision.REVIEW);
  }
}
