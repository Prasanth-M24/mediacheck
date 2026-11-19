package com.mediacheck.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AiExplanationProviderTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AiExplanationProvider provider = new AiExplanationProvider(mapper, true, "template", "", "gemini-1.5-flash");

  @Test
  void generatesDeterministicFallbackForApprovedDecision() {
    var findings = List.of(
        new MediaReviewDtos.Finding("1", "PROVENANCE", "c2pa_verification", "PASS", "Valid credentials", "{}"),
        new MediaReviewDtos.Finding("2", "RIGHTS", "rights_asset_matching", "PASS", "No rights exposure", "{}")
    );
    var reasons = List.of("All verification checks passed without conflict");

    var result = provider.generateExplanation("APPROVE", "hero.png", findings, reasons, "phase3-2.0");

    assertThat(result.explanationStatus()).isEqualTo("DETERMINISTIC_FALLBACK");
    assertThat(result.explanationText()).contains("Decision: APPROVE");
    assertThat(result.explanationText()).contains("hero.png");
    assertThat(result.explanationText()).contains("CLEARED FOR DEPLOYMENT");
  }

  @Test
  void generatesDeterministicFallbackForBlockedDecision() {
    var findings = List.of(
        new MediaReviewDtos.Finding("1", "RIGHTS", "licence_applicability", "FAIL", "Licence EXPIRED", "{}")
    );
    var reasons = List.of("One or more critical checks failed mandatory requirements");

    var result = provider.generateExplanation("BLOCK", "sample.jpg", findings, reasons, "phase3-2.0");

    assertThat(result.explanationStatus()).isEqualTo("DETERMINISTIC_FALLBACK");
    assertThat(result.explanationText()).contains("Decision: BLOCK");
    assertThat(result.explanationText()).contains("CRITICAL ISSUES PREVENTING APPROVAL");
    assertThat(result.explanationText()).contains("Licence EXPIRED");
  }

  @Test
  void returnsUnavailableWhenDisabled() {
    var disabledProvider = new AiExplanationProvider(mapper, false, "template", "", "gemini-1.5-flash");
    var result = disabledProvider.generateExplanation("APPROVE", "img.jpg", List.of(), List.of(), "phase3-2.0");

    assertThat(result.explanationStatus()).isEqualTo("UNAVAILABLE");
    assertThat(result.explanationText()).contains("disabled");
  }
}
