package com.mediacheck.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediacheck.domain.EvidenceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Deployment Readiness Policy Engine.
 * Evaluates active company policies against review context and analysis evidence.
 */
public final class PolicyEngine {
  private static final ObjectMapper mapper = new ObjectMapper();

  private PolicyEngine() {}

  public record PolicyRuleEvaluation(
      String ruleId,
      String ruleName,
      EvidenceStatus status,
      String message
  ) {}

  public record PolicyEvaluationOutcome(
      EvidenceStatus overallStatus,
      String summary,
      List<PolicyRuleEvaluation> ruleEvaluations
  ) {}

  public static PolicyEvaluationOutcome evaluate(
      String rulesJson,
      String intendedChannel,
      String intendedTerritory,
      String intendedUse,
      String aiGenerationStatus,
      String c2paStatus,
      boolean provenanceAvailable
  ) {
    List<PolicyRuleEvaluation> evaluations = new ArrayList<>();
    JsonNode rules = null;
    try {
      if (rulesJson != null && !rulesJson.isBlank()) {
        rules = mapper.readTree(rulesJson);
      }
    } catch (Exception e) {
      // Fall through with empty rules
    }

    if (rules == null || rules.isEmpty()) {
      return new PolicyEvaluationOutcome(
          EvidenceStatus.PASS,
          "No specific deployment readiness constraints triggered for current context.",
          List.of(new PolicyRuleEvaluation("DEFAULT_ALLOW", "Default policy", EvidenceStatus.PASS, "No conflicting rules."))
      );
    }

    // 1. Mandatory metadata rule
    if (rules.has("requireMandatoryMetadata") && rules.get("requireMandatoryMetadata").asBoolean()) {
      boolean missing = (intendedChannel == null || intendedChannel.isBlank())
          || (intendedTerritory == null || intendedTerritory.isBlank())
          || (intendedUse == null || intendedUse.isBlank());
      if (missing) {
        evaluations.add(new PolicyRuleEvaluation(
            "MANDATORY_METADATA",
            "Mandatory Publication Context",
            EvidenceStatus.ADVISORY_FLAG,
            "Intended publication channel, territory, and use must be fully specified prior to release."
        ));
      } else {
        evaluations.add(new PolicyRuleEvaluation(
            "MANDATORY_METADATA",
            "Mandatory Publication Context",
            EvidenceStatus.PASS,
            "Required publication metadata is present."
        ));
      }
    }

    // 2. Channel restrictions
    if (rules.has("allowedChannels") && rules.get("allowedChannels").isArray()) {
      List<String> allowed = new ArrayList<>();
      rules.get("allowedChannels").forEach(c -> allowed.add(c.asText().toUpperCase()));
      if (!allowed.isEmpty()) {
        if (intendedChannel == null || intendedChannel.isBlank()) {
          evaluations.add(new PolicyRuleEvaluation(
              "CHANNEL_RESTRICTION",
              "Channel Policy",
              EvidenceStatus.ADVISORY_FLAG,
              "Publication channel is not specified. Allowed channels: " + allowed
          ));
        } else if (!allowed.contains(intendedChannel.toUpperCase()) && !allowed.contains("ALL")) {
          evaluations.add(new PolicyRuleEvaluation(
              "CHANNEL_RESTRICTION",
              "Channel Policy",
              EvidenceStatus.FAIL,
              "Intended channel '" + intendedChannel + "' violates policy. Allowed: " + allowed
          ));
        } else {
          evaluations.add(new PolicyRuleEvaluation(
              "CHANNEL_RESTRICTION",
              "Channel Policy",
              EvidenceStatus.PASS,
              "Intended channel '" + intendedChannel + "' is permitted by policy."
          ));
        }
      }
    }

    // 3. Territory restrictions
    if (rules.has("allowedTerritories") && rules.get("allowedTerritories").isArray()) {
      List<String> allowed = new ArrayList<>();
      rules.get("allowedTerritories").forEach(t -> allowed.add(t.asText().toUpperCase()));
      if (!allowed.isEmpty()) {
        if (intendedTerritory == null || intendedTerritory.isBlank()) {
          evaluations.add(new PolicyRuleEvaluation(
              "TERRITORY_RESTRICTION",
              "Territory Policy",
              EvidenceStatus.ADVISORY_FLAG,
              "Publication territory is not specified. Allowed territories: " + allowed
          ));
        } else if (!allowed.contains(intendedTerritory.toUpperCase()) && !allowed.contains("GLOBAL")) {
          evaluations.add(new PolicyRuleEvaluation(
              "TERRITORY_RESTRICTION",
              "Territory Policy",
              EvidenceStatus.FAIL,
              "Intended territory '" + intendedTerritory + "' violates policy. Allowed: " + allowed
          ));
        } else {
          evaluations.add(new PolicyRuleEvaluation(
              "TERRITORY_RESTRICTION",
              "Territory Policy",
              EvidenceStatus.PASS,
              "Intended territory '" + intendedTerritory + "' is permitted by policy."
          ));
        }
      }
    }

    // 4. AI Disclosure requirement
    if (rules.has("requireAiDisclosure") && rules.get("requireAiDisclosure").asBoolean()) {
      if ("DETECTED".equalsIgnoreCase(aiGenerationStatus) || "LIKELY_AI_GENERATED".equalsIgnoreCase(aiGenerationStatus)) {
        evaluations.add(new PolicyRuleEvaluation(
            "AI_DISCLOSURE",
            "Mandatory AI Disclosure",
            EvidenceStatus.ADVISORY_FLAG,
            "AI generation indicators present. Content requires clear consumer disclosure label prior to distribution."
        ));
      } else if (!provenanceAvailable) {
        evaluations.add(new PolicyRuleEvaluation(
            "AI_DISCLOSURE",
            "Mandatory AI Disclosure",
            EvidenceStatus.ADVISORY_FLAG,
            "AI generation status is unverified; disclosure audit review recommended."
        ));
      } else {
        evaluations.add(new PolicyRuleEvaluation(
            "AI_DISCLOSURE",
            "Mandatory AI Disclosure",
            EvidenceStatus.PASS,
            "No AI disclosure requirement triggered."
        ));
      }
    }

    // 5. Hard Block on AI generated content
    if (rules.has("blockAiGenerated") && rules.get("blockAiGenerated").asBoolean()) {
      if ("DETECTED".equalsIgnoreCase(aiGenerationStatus)) {
        evaluations.add(new PolicyRuleEvaluation(
            "BLOCK_AI_GENERATED",
            "Prohibit Synthetic Media",
            EvidenceStatus.FAIL,
            "Policy strictly prohibits synthetic media. AI generation indicators were detected."
        ));
      } else if ("LIKELY_AI_GENERATED".equalsIgnoreCase(aiGenerationStatus)) {
        evaluations.add(new PolicyRuleEvaluation(
            "BLOCK_AI_GENERATED",
            "Prohibit Synthetic Media",
            EvidenceStatus.ADVISORY_FLAG,
            "Likely synthetic patterns detected under zero-tolerance AI policy; human sign-off required."
        ));
      }
    }

    // 6. C2PA provenance requirement
    if (rules.has("requireValidC2pa") && rules.get("requireValidC2pa").asBoolean()) {
      if (!"DETECTED".equalsIgnoreCase(c2paStatus)) {
        evaluations.add(new PolicyRuleEvaluation(
            "REQUIRE_C2PA",
            "Mandatory Content Credentials",
            EvidenceStatus.FAIL,
            "Policy requires cryptographic C2PA content credentials, but none were detected."
        ));
      } else {
        evaluations.add(new PolicyRuleEvaluation(
            "REQUIRE_C2PA",
            "Mandatory Content Credentials",
            EvidenceStatus.PASS,
            "Valid C2PA content credentials verified."
        ));
      }
    }

    // 7. Block on unavailable provenance
    if (rules.has("blockOnUnavailableProvenance") && rules.get("blockOnUnavailableProvenance").asBoolean()) {
      if (!provenanceAvailable) {
        evaluations.add(new PolicyRuleEvaluation(
            "PROVENANCE_AVAILABILITY",
            "Provenance Verification Gate",
            EvidenceStatus.FAIL,
            "Policy requires full provenance availability, but the analysis service was unavailable."
        ));
      }
    }

    // Determine aggregate outcome
    boolean hasFail = evaluations.stream().anyMatch(e -> e.status() == EvidenceStatus.FAIL);
    boolean hasAdvisory = evaluations.stream().anyMatch(e -> e.status() == EvidenceStatus.ADVISORY_FLAG);

    EvidenceStatus outcomeStatus = hasFail ? EvidenceStatus.FAIL : hasAdvisory ? EvidenceStatus.ADVISORY_FLAG : EvidenceStatus.PASS;
    String summary = hasFail
        ? "Deployment blocked by company policy rule violations."
        : hasAdvisory
            ? "Deployment policy requirements triggered advisory flags requiring reviewer acknowledgment."
            : "All applicable company deployment policies satisfied.";

    return new PolicyEvaluationOutcome(outcomeStatus, summary, evaluations);
  }
}
