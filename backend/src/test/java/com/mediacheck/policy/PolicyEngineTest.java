package com.mediacheck.policy;

import com.mediacheck.domain.EvidenceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicyEngineTest {

  @Test
  void passesWhenRulesAreEmpty() {
    var outcome = PolicyEngine.evaluate("{}", "WEB", "US", "COMMERCIAL", "NOT_DETECTED", "DETECTED", true);
    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.PASS);
  }

  @Test
  void advisoryWhenAiDisclosureRequiredAndSyntheticDetected() {
    String rules = "{\"requireAiDisclosure\":true}";
    var outcome = PolicyEngine.evaluate(rules, "WEB", "US", "COMMERCIAL", "DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.ADVISORY_FLAG);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("AI_DISCLOSURE") && r.status() == EvidenceStatus.ADVISORY_FLAG);
  }

  @Test
  void failsWhenBlockAiGeneratedIsConfiguredAndAiDetected() {
    String rules = "{\"blockAiGenerated\":true}";
    var outcome = PolicyEngine.evaluate(rules, "WEB", "US", "COMMERCIAL", "DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("BLOCK_AI_GENERATED") && r.status() == EvidenceStatus.FAIL);
  }

  @Test
  void failsWhenChannelOutsideAllowedList() {
    String rules = "{\"allowedChannels\":[\"WEB\",\"SOCIAL\"]}";
    var outcome = PolicyEngine.evaluate(rules, "TV_BROADCAST", "US", "COMMERCIAL", "NOT_DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("CHANNEL_RESTRICTION") && r.status() == EvidenceStatus.FAIL);
  }

  @Test
  void passesWhenChannelWithinAllowedList() {
    String rules = "{\"allowedChannels\":[\"WEB\",\"SOCIAL\"]}";
    var outcome = PolicyEngine.evaluate(rules, "SOCIAL", "US", "COMMERCIAL", "NOT_DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.PASS);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("CHANNEL_RESTRICTION") && r.status() == EvidenceStatus.PASS);
  }

  @Test
  void failsWhenC2paRequiredButNotDetected() {
    String rules = "{\"requireValidC2pa\":true}";
    var outcome = PolicyEngine.evaluate(rules, "WEB", "US", "COMMERCIAL", "NOT_DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.FAIL);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("REQUIRE_C2PA") && r.status() == EvidenceStatus.FAIL);
  }

  @Test
  void advisoryWhenMandatoryMetadataIsMissing() {
    String rules = "{\"requireMandatoryMetadata\":true}";
    var outcome = PolicyEngine.evaluate(rules, null, "US", "COMMERCIAL", "NOT_DETECTED", "NOT_DETECTED", true);

    assertThat(outcome.overallStatus()).isEqualTo(EvidenceStatus.ADVISORY_FLAG);
    assertThat(outcome.ruleEvaluations()).anyMatch(r -> r.ruleId().equals("MANDATORY_METADATA") && r.status() == EvidenceStatus.ADVISORY_FLAG);
  }
}
