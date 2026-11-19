package com.mediacheck.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediacheck.domain.EvidenceStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates whether registered company licence records authorize the intended
 * publication context (channel, territory, use, planned date) for matched rights assets.
 */
public final class LicenceApplicabilityEngine {
  private static final ObjectMapper mapper = new ObjectMapper();

  private LicenceApplicabilityEngine() {}

  public record LicenceEvaluationResult(
      EvidenceStatus status,
      String summary,
      List<String> violations,
      List<Map<String, Object>> licenceDetails
  ) {}

  public static LicenceEvaluationResult evaluate(
      List<Map<String, Object>> matchedAssets,
      List<Map<String, Object>> licences,
      String intendedChannel,
      String intendedTerritory,
      String intendedUse,
      Instant plannedPublicationAt
  ) {
    if (matchedAssets == null || matchedAssets.isEmpty()) {
      return new LicenceEvaluationResult(
          EvidenceStatus.PASS,
          "No proprietary company rights assets matched. Licence applicability evaluation is not required.",
          List.of(),
          List.of()
      );
    }

    if (licences == null || licences.isEmpty()) {
      // Asset matched, but no licence record found on file
      return new LicenceEvaluationResult(
          EvidenceStatus.ADVISORY_FLAG,
          "Matched proprietary company rights asset has no registered licence records on file. Verification required prior to deployment.",
          List.of("No licence record found for matched rights asset"),
          List.of()
      );
    }

    Instant evalTime = plannedPublicationAt != null ? plannedPublicationAt : Instant.now();
    List<String> violations = new ArrayList<>();
    List<Map<String, Object>> detailsList = new ArrayList<>();
    boolean hasValidActiveLicence = false;

    for (Map<String, Object> lic : licences) {
      UUID licId = (UUID) lic.get("id");
      String ref = (String) lic.get("reference");
      String licStatus = (String) lic.get("status");
      Instant validFrom = (Instant) lic.get("validFrom");
      Instant validUntil = (Instant) lic.get("validUntil");
      String termsJson = (String) lic.get("termsJson");

      List<String> licViolations = new ArrayList<>();

      // 1. Lifecycle status check
      if ("EXPIRED".equalsIgnoreCase(licStatus)) {
        licViolations.add("Licence " + ref + " lifecycle status is EXPIRED.");
      } else if ("REVOKED".equalsIgnoreCase(licStatus)) {
        licViolations.add("Licence " + ref + " lifecycle status is REVOKED.");
      } else if ("SUSPENDED".equalsIgnoreCase(licStatus)) {
        licViolations.add("Licence " + ref + " lifecycle status is SUSPENDED.");
      } else if ("DRAFT".equalsIgnoreCase(licStatus)) {
        licViolations.add("Licence " + ref + " is in DRAFT state and not yet active.");
      }

      // 2. Date validity check
      if (validFrom != null && evalTime.isBefore(validFrom)) {
        licViolations.add("Planned publication date " + evalTime + " is before licence start date " + validFrom);
      }
      if (validUntil != null && evalTime.isAfter(validUntil)) {
        licViolations.add("Planned publication date " + evalTime + " is after licence expiry date " + validUntil);
      }

      // 3. Terms JSON parsing (channels, territories, permittedUse)
      if (termsJson != null && !termsJson.isBlank()) {
        try {
          JsonNode terms = mapper.readTree(termsJson);

          // Channel check
          if (intendedChannel != null && !intendedChannel.isBlank() && terms.has("permittedChannels")) {
            List<String> allowedChannels = new ArrayList<>();
            terms.get("permittedChannels").forEach(c -> allowedChannels.add(c.asText().toUpperCase()));
            if (!allowedChannels.isEmpty() && !allowedChannels.contains(intendedChannel.toUpperCase()) && !allowedChannels.contains("ALL")) {
              licViolations.add("Intended channel '" + intendedChannel + "' is not permitted by licence " + ref + " (allowed: " + allowedChannels + ")");
            }
          }

          // Territory check
          if (intendedTerritory != null && !intendedTerritory.isBlank() && terms.has("permittedTerritories")) {
            List<String> allowedTerritories = new ArrayList<>();
            terms.get("permittedTerritories").forEach(t -> allowedTerritories.add(t.asText().toUpperCase()));
            if (!allowedTerritories.isEmpty() && !allowedTerritories.contains(intendedTerritory.toUpperCase()) && !allowedTerritories.contains("GLOBAL")) {
              licViolations.add("Intended territory '" + intendedTerritory + "' is not permitted by licence " + ref + " (allowed: " + allowedTerritories + ")");
            }
          }

          // Permitted use check
          if (intendedUse != null && !intendedUse.isBlank() && terms.has("permittedUse")) {
            String allowedUse = terms.get("permittedUse").asText().toUpperCase();
            if (!allowedUse.equals("ANY") && !allowedUse.equals(intendedUse.toUpperCase())) {
              licViolations.add("Intended use '" + intendedUse + "' does not match licence permitted use '" + allowedUse + "'");
            }
          }

        } catch (Exception e) {
          licViolations.add("Malformed terms JSON for licence " + ref);
        }
      }

      boolean licValid = licViolations.isEmpty() && "ACTIVE".equalsIgnoreCase(licStatus);
      if (licValid) {
        hasValidActiveLicence = true;
      } else {
        violations.addAll(licViolations);
      }

      detailsList.add(Map.of(
          "licenceId", licId.toString(),
          "reference", ref != null ? ref : "UNKNOWN",
          "status", licStatus != null ? licStatus : "UNKNOWN",
          "valid", licValid,
          "violations", licViolations
      ));
    }

    if (hasValidActiveLicence) {
      return new LicenceEvaluationResult(
          EvidenceStatus.PASS,
          "Active licence coverage confirmed for matched rights asset under the specified publication context.",
          List.of(),
          detailsList
      );
    } else {
      // Licence exists but conditions failed
      boolean isHardFailure = violations.stream().anyMatch(v -> v.contains("EXPIRED") || v.contains("REVOKED") || v.contains("not permitted"));
      return new LicenceEvaluationResult(
          isHardFailure ? EvidenceStatus.FAIL : EvidenceStatus.ADVISORY_FLAG,
          "Licence restrictions or validity issues identified for matched rights asset.",
          violations,
          detailsList
      );
    }
  }
}
