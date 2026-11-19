package com.mediacheck.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Backend-only AI Explanation Provider.
 * Generates natural-language risk summaries and explanations strictly based on persisted evidence.
 *
 * CRITICAL RULE: AI explanations NEVER alter, participate in, or override the deterministic decision.
 */
@Component
public class AiExplanationProvider {
  private static final Logger log = LoggerFactory.getLogger(AiExplanationProvider.class);
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  private final boolean enabled;
  private final String provider;
  private final String apiKey;
  private final String model;

  public AiExplanationProvider(
      ObjectMapper mapper,
      @Value("${mediacheck.ai-explanation.enabled:true}") boolean enabled,
      @Value("${mediacheck.ai-explanation.provider:template}") String provider,
      @Value("${mediacheck.ai-explanation.api-key:}") String apiKey,
      @Value("${mediacheck.ai-explanation.model:gemini-1.5-flash}") String model
  ) {
    this.mapper = mapper;
    this.enabled = enabled;
    this.provider = provider != null ? provider.trim().toLowerCase() : "template";
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.model = model != null ? model.trim() : "gemini-1.5-flash";
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  public record ExplanationResult(
      String explanationStatus,
      String explanationText,
      String providerUsed,
      String modelUsed
  ) {}

  public ExplanationResult generateExplanation(
      String deterministicDecision,
      String originalFilename,
      List<MediaReviewDtos.Finding> findings,
      List<String> reasons,
      String policyVersion
  ) {
    if (!enabled) {
      return new ExplanationResult(
          "UNAVAILABLE",
          "AI explanation provider is disabled by server configuration.",
          "none",
          "none"
      );
    }

    // If an external provider (like Gemini) is configured with a valid API key, attempt live generation
    if ("gemini".equals(provider) && !apiKey.isBlank()) {
      try {
        return callGemini(deterministicDecision, originalFilename, findings, reasons, policyVersion);
      } catch (Exception e) {
        log.warn("Gemini API explanation request failed: {}. Falling back to deterministic structured summary.", e.getMessage());
      }
    }

    // High-fidelity structured fallback based strictly on actual persisted evidence
    return generateDeterministicFallback(deterministicDecision, originalFilename, findings, reasons, policyVersion);
  }

  private ExplanationResult generateDeterministicFallback(
      String decision,
      String filename,
      List<MediaReviewDtos.Finding> findings,
      List<String> reasons,
      String policyVersion
  ) {
    StringBuilder sb = new StringBuilder();
    sb.append("Deterministic Review Summary for ").append(filename).append(" (Decision: ").append(decision).append("):\n\n");

    long passCount = findings.stream().filter(f -> "PASS".equalsIgnoreCase(f.status())).count();
    long advisoryCount = findings.stream().filter(f -> "ADVISORY_FLAG".equalsIgnoreCase(f.status())).count();
    long failCount = findings.stream().filter(f -> "FAIL".equalsIgnoreCase(f.status())).count();
    long unavailCount = findings.stream().filter(f -> "UNAVAILABLE".equalsIgnoreCase(f.status())).count();

    sb.append("Analysis overview: ").append(passCount).append(" checks passed, ")
        .append(advisoryCount).append(" advisory flags, ")
        .append(failCount).append(" failures, ")
        .append(unavailCount).append(" unavailable.\n\n");

    if ("BLOCK".equals(decision)) {
      sb.append("CRITICAL ISSUES PREVENTING APPROVAL:\n");
      findings.stream()
          .filter(f -> "FAIL".equalsIgnoreCase(f.status()))
          .forEach(f -> sb.append("• [").append(f.category()).append("] ").append(f.summary()).append("\n"));
      sb.append("\nRecommended Next Steps: Resolve identified licence conflicts or policy violations before re-submitting.\n");
    } else if ("REVIEW".equals(decision)) {
      sb.append("ADVISORY ITEMS REQUIRING HUMAN REVIEW:\n");
      findings.stream()
          .filter(f -> "ADVISORY_FLAG".equalsIgnoreCase(f.status()) || "UNAVAILABLE".equalsIgnoreCase(f.status()))
          .forEach(f -> sb.append("• [").append(f.category()).append("] ").append(f.summary()).append("\n"));
      sb.append("\nRecommended Next Steps: A designated compliance reviewer must review the flagged items and confirm clearance.\n");
    } else {
      sb.append("CLEARED FOR DEPLOYMENT:\n");
      sb.append("All technical provenance, rights asset matching, licence applicability, and deployment policies satisfied current requirements under policy ").append(policyVersion).append(".\n");
    }

    sb.append("\nNote: Explanation synthesized directly from verified deterministic check results. Deterministic decision engine remains the sole authority.");

    return new ExplanationResult(
        "DETERMINISTIC_FALLBACK",
        sb.toString(),
        "deterministic-rule-engine",
        "phase3-2.0"
    );
  }

  private ExplanationResult callGemini(
      String decision,
      String filename,
      List<MediaReviewDtos.Finding> findings,
      List<String> reasons,
      String policyVersion
  ) throws Exception {
    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

    String systemPrompt = "You are a compliance assistant explaining a deterministic media review decision. " +
        "CRITICAL RULE: The final decision is ALREADY DETERMINED as " + decision + " and CANNOT BE OVERRIDDEN OR ALTERED. " +
        "Explain the reasons for this decision clearly, highlight specific evidence findings, and provide actionable next steps.";

    StringBuilder userContent = new StringBuilder();
    userContent.append("Media file: ").append(filename).append("\n");
    userContent.append("Deterministic Decision: ").append(decision).append("\n");
    userContent.append("Policy Version: ").append(policyVersion).append("\n");
    userContent.append("Findings:\n");
    for (var f : findings) {
      userContent.append("- [").append(f.category()).append("] ").append(f.checkName()).append(": ").append(f.status()).append(" - ").append(f.summary()).append("\n");
    }

    String requestBody = mapper.writeValueAsString(Map.of(
        "contents", List.of(
            Map.of("role", "user", "parts", List.of(Map.of("text", systemPrompt + "\n\n" + userContent.toString())))
        ),
        "generationConfig", Map.of(
            "temperature", 0.2,
            "maxOutputTokens", 500
        )
    ));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .timeout(Duration.ofSeconds(8))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Gemini API returned status " + response.statusCode() + ": " + response.body());
    }

    JsonNode json = mapper.readTree(response.body());
    String text = json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
    if (text == null || text.isBlank()) {
      throw new RuntimeException("Empty response text from Gemini API");
    }

    return new ExplanationResult(
        "COMPLETED",
        text.trim(),
        "gemini",
        model
    );
  }
}
