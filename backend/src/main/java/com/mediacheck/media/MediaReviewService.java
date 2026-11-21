package com.mediacheck.media;

import static com.mediacheck.media.MediaReviewDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediacheck.domain.EvidenceStatus;
import com.mediacheck.domain.PolicyDecision;
import com.mediacheck.policy.PolicyEngine;
import com.mediacheck.storage.StorageService;
import com.mediacheck.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class MediaReviewService {
  private static final Logger log = LoggerFactory.getLogger(MediaReviewService.class);
  private static final List<String> SUPPORTED_TYPES = List.of("image/jpeg", "image/png", "image/webp");

  private final JdbcTemplate jdbc;
  private final StorageService storage;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;
  private final AiExplanationProvider explanationProvider;
  private final HttpClient httpClient;
  private final long maxFileSize;
  private final String aiServiceBaseUrl;

  public MediaReviewService(
      JdbcTemplate jdbc,
      StorageService storage,
      ObjectMapper mapper,
      ApplicationEventPublisher events,
      AiExplanationProvider explanationProvider,
      @Value("${mediacheck.media.max-file-size:52428800}") long maxFileSize,
      @Value("${mediacheck.ai-service-base-url:http://localhost:8000}") String aiServiceBaseUrl
  ) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.mapper = mapper;
    this.events = events;
    this.explanationProvider = explanationProvider;
    this.maxFileSize = maxFileSize;
    this.aiServiceBaseUrl = aiServiceBaseUrl != null ? aiServiceBaseUrl.replaceAll("/+$", "") : "http://localhost:8000";
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  }

  @Transactional
  public MediaDetail upload(
      UUID company,
      UUID user,
      UUID membership,
      MultipartFile file,
      String intendedChannel,
      String intendedTerritory,
      String intendedUse,
      Instant plannedPublicationAt
  ) {
    if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
      throw bad("invalid_media_size");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw bad("media_read_failed");
    }

    String mime = detectMime(bytes);
    if (!SUPPORTED_TYPES.contains(mime)) {
      throw bad("unsupported_media_type");
    }

    String checksum = sha256(bytes);
    if (jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE company_id=? AND sha256_checksum=?", Long.class, company, checksum) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicate_media");
    }

    Dimension d = dimensions(bytes);
    String perceptualHash = ImageFingerprint.differenceHash(bytes);
    UUID mediaId = UUID.randomUUID();
    StoredFile stored;
    try {
      stored = storage.store("media", company, mediaId, extension(mime), new ByteArrayInputStream(bytes), bytes.length);
    } catch (RuntimeException e) {
      throw bad("media_storage_failed");
    }

    UUID jobId = UUID.randomUUID();
    UUID outboxId = UUID.randomUUID();

    try {
      jdbc.update(
          "INSERT INTO media_assets (id, company_id, uploaded_by_user_id, uploaded_by_membership_id, original_filename, storage_key, storage_provider, mime_type, file_size_bytes, sha256_checksum, width_pixels, height_pixels, lifecycle_status, perceptual_hash, intended_channel, intended_territory, intended_use, planned_publication_at) " +
          "VALUES (?, ?, ?, ?, ?, ?, 'local', ?, ?, ?, ?, ?, 'UPLOADED', ?, ?, ?, ?, ?)",
          mediaId, company, user, membership, safeFilename(file.getOriginalFilename()), stored.storageKey(),
          mime, bytes.length, checksum, d.width(), d.height(), perceptualHash,
          blank(intendedChannel), blank(intendedTerritory), blank(intendedUse),
          plannedPublicationAt == null ? null : Timestamp.from(plannedPublicationAt)
      );

      jdbc.update("INSERT INTO scan_jobs (id, company_id, media_asset_id, status, retry_count) VALUES (?, ?, ?, 'QUEUED', 0)",
          jobId, company, mediaId);

      String outboxPayload = json(Map.of(
          "jobId", jobId.toString(),
          "mediaId", mediaId.toString(),
          "companyId", company.toString()
      ));
      jdbc.update("INSERT INTO outbox_messages (id, company_id, event_type, payload_json, status, retry_count) VALUES (?, ?, 'MEDIA_SCAN_REQUESTED', ?, 'PENDING', 0)",
          outboxId, company, outboxPayload);

      audit(company, user, membership, "MEDIA_UPLOADED", mediaId, Map.of("sha256", checksum, "mimeType", mime));

      events.publishEvent(new MediaQueuedEvent(jobId, outboxId));
    } catch (RuntimeException e) {
      storage.delete(stored.storageKey());
      throw e;
    }

    return detail(company, mediaId);
  }

  @Transactional(readOnly = true)
  public PagedMedia list(UUID company, int page, int size) {
    int s = Math.min(Math.max(size, 1), 100);
    int p = Math.max(page, 0);
    List<MediaSummary> rows = jdbc.query(
        "SELECT m.id, m.original_filename, m.mime_type, m.file_size_bytes, m.sha256_checksum, m.lifecycle_status, j.status, COALESCE(d.decision,''), m.uploaded_at " +
        "FROM media_assets m " +
        "JOIN scan_jobs j ON j.company_id=m.company_id AND j.media_asset_id=m.id " +
        "LEFT JOIN decisions d ON d.company_id=j.company_id AND d.scan_job_id=j.id " +
        "WHERE m.company_id=? AND m.lifecycle_status<>'DELETED' " +
        "ORDER BY m.uploaded_at DESC LIMIT ? OFFSET ?",
        (rs, n) -> new MediaSummary(
            rs.getObject(1, UUID.class),
            rs.getString(2),
            rs.getString(3),
            rs.getLong(4),
            rs.getString(5),
            rs.getString(6),
            rs.getString(7),
            rs.getString(8),
            rs.getTimestamp(9).toInstant()
        ),
        company, s, p * s
    );
    long total = jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE company_id=? AND lifecycle_status<>'DELETED'", Long.class, company);
    return new PagedMedia(rows, p, s, total, (int) Math.ceil((double) total / s));
  }

  @Transactional(readOnly = true)
  public MediaDetail detail(UUID company, UUID id) {
    MediaDetail d = jdbc.query(
        "SELECT m.id, m.company_id, m.original_filename, m.mime_type, m.file_size_bytes, m.sha256_checksum, m.width_pixels, m.height_pixels, m.lifecycle_status, j.status, COALESCE(d.decision,''), m.intended_channel, m.intended_territory, m.intended_use, m.planned_publication_at, m.uploaded_at, m.updated_at " +
        "FROM media_assets m " +
        "JOIN scan_jobs j ON j.company_id=m.company_id AND j.media_asset_id=m.id " +
        "LEFT JOIN decisions d ON d.company_id=j.company_id AND d.scan_job_id=j.id " +
        "WHERE m.company_id=? AND m.id=?",
        rs -> {
          if (!rs.next()) return null;
          return new MediaDetail(
              rs.getObject(1, UUID.class),
              rs.getObject(2, UUID.class),
              rs.getString(3),
              rs.getString(4),
              rs.getLong(5),
              rs.getString(6),
              (Integer) rs.getObject(7),
              (Integer) rs.getObject(8),
              rs.getString(9),
              rs.getString(10),
              rs.getString(11),
              rs.getString(12),
              rs.getString(13),
              rs.getString(14),
              rs.getTimestamp(15) == null ? null : rs.getTimestamp(15).toInstant(),
              rs.getTimestamp(16).toInstant(),
              rs.getTimestamp(17).toInstant()
          );
        },
        company, id
    );
    if (d == null) throw notFound("media_not_found");
    return d;
  }

  public InputStream file(UUID company, UUID id) {
    String key = jdbc.query(
        "SELECT storage_key FROM media_assets WHERE company_id=? AND id=? AND lifecycle_status<>'DELETED'",
        rs -> rs.next() ? rs.getString(1) : null,
        company, id
    );
    if (key == null) throw notFound("media_not_found");
    return storage.retrieve(key);
  }

  @Transactional
  public void archive(UUID company, UUID user, UUID membership, UUID id) {
    if (jdbc.update("UPDATE media_assets SET lifecycle_status='DELETED', updated_at=CURRENT_TIMESTAMP WHERE company_id=? AND id=? AND lifecycle_status<>'DELETED'", company, id) == 0) {
      throw notFound("media_not_found");
    }
    audit(company, user, membership, "MEDIA_ARCHIVED", id, Map.of());
  }

  @Transactional(readOnly = true)
  public ReviewReport report(UUID company, UUID media) {
    UUID jobId = jdbc.query("SELECT id FROM scan_jobs WHERE company_id=? AND media_asset_id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, company, media);
    if (jobId == null) throw notFound("media_not_found");

    return jdbc.query(
        "SELECT r.id, r.report_status, r.explanation_status, r.report_json, d.decision, pv.version_number, m.original_filename, m.mime_type, m.file_size_bytes, m.sha256_checksum, m.width_pixels, m.height_pixels, m.intended_channel, m.intended_territory, m.intended_use, m.planned_publication_at, d.reasons " +
        "FROM reports r " +
        "JOIN decisions d ON d.company_id=r.company_id AND d.id=r.decision_id " +
        "JOIN policy_versions pv ON pv.company_id=d.company_id AND pv.id=d.policy_version_id " +
        "JOIN scan_jobs j ON j.id=d.scan_job_id " +
        "JOIN media_assets m ON m.id=j.media_asset_id " +
        "WHERE r.company_id=? AND d.scan_job_id=?",
        rs -> {
          if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report_not_ready");
          UUID reportId = rs.getObject(1, UUID.class);
          String reportStatus = rs.getString(2);
          String explStatus = rs.getString(3);
          String reportJsonStr = rs.getString(4);
          String decision = rs.getString(5);
          String policyVersion = "v" + rs.getInt(6);

          String origName = rs.getString(7);
          String mime = rs.getString(8);
          long size = rs.getLong(9);
          String sha = rs.getString(10);
          Integer w = (Integer) rs.getObject(11);
          Integer h = (Integer) rs.getObject(12);
          String channel = rs.getString(13);
          String territory = rs.getString(14);
          String use = rs.getString(15);
          Instant plannedAt = rs.getTimestamp(16) == null ? null : rs.getTimestamp(16).toInstant();
          String reasonsJson = rs.getString(17);

          List<String> reasons = new ArrayList<>();
          try {
            if (reasonsJson != null) {
              mapper.readTree(reasonsJson).forEach(r -> reasons.add(r.asText()));
            }
          } catch (Exception ignored) {}

          List<Finding> findings = jdbc.query(
              "SELECT f.id, p.pipeline_type, f.finding_type, f.status, f.summary, COALESCE(f.details_json::text, '{}') " +
              "FROM findings f " +
              "JOIN pipeline_runs p ON p.company_id=f.company_id AND p.id=f.pipeline_run_id " +
              "WHERE f.company_id=? AND p.scan_job_id=? ORDER BY f.created_at",
              (x, n) -> new Finding(
                  x.getObject(1, UUID.class).toString(),
                  x.getString(2),
                  x.getString(3),
                  x.getString(4),
                  x.getString(5),
                  x.getString(6)
              ),
              company, jobId
          );

          String explanationText = "Deterministic summary unavailable.";
          try {
            if (reportJsonStr != null) {
              JsonNode rj = mapper.readTree(reportJsonStr);
              if (rj.has("explanation")) {
                explanationText = rj.get("explanation").asText();
              }
            }
          } catch (Exception ignored) {}

          return new ReviewReport(
              reportId,
              media,
              origName,
              mime,
              size,
              sha,
              w,
              h,
              channel,
              territory,
              use,
              plannedAt,
              reportStatus,
              decision,
              policyVersion,
              explStatus,
              explanationText,
              reasons,
              findings,
              Instant.now()
          );
        },
        company, jobId
    );
  }

  @Transactional(readOnly = true)
  public List<ReportSummary> reports(UUID company) {
    return jdbc.query(
        "SELECT r.id, m.id, m.original_filename, d.decision, r.report_status, r.created_at " +
        "FROM reports r " +
        "JOIN decisions d ON d.company_id=r.company_id AND d.id=r.decision_id " +
        "JOIN scan_jobs j ON j.company_id=d.company_id AND j.id=d.scan_job_id " +
        "JOIN media_assets m ON m.company_id=j.company_id AND m.id=j.media_asset_id " +
        "WHERE r.company_id=? " +
        "ORDER BY r.created_at DESC",
        (rs, n) -> new ReportSummary(
            rs.getObject(1, UUID.class),
            rs.getObject(2, UUID.class),
            rs.getString(3),
            rs.getString(4),
            rs.getString(5),
            rs.getTimestamp(6).toInstant()
        ),
        company
    );
  }

  @Transactional
  public void process(UUID job) {
    Map<String, Object> row = jdbc.query(
        "SELECT company_id, media_asset_id, status, started_at FROM scan_jobs WHERE id=?",
        rs -> {
          if (!rs.next()) return null;
          return Map.of(
              "company", rs.getObject(1, UUID.class),
              "media", rs.getObject(2, UUID.class),
              "status", rs.getString(3),
              "startedAt", rs.getTimestamp(4) == null ? Instant.EPOCH : rs.getTimestamp(4).toInstant()
          );
        },
        job
    );

    if (row == null) {
      log.warn("Job {} not found in database; ignoring.", job);
      return;
    }

    String currentStatus = (String) row.get("status");
    if ("COMPLETED".equals(currentStatus) || jdbc.queryForObject("SELECT COUNT(*) FROM decisions WHERE scan_job_id=?", Long.class, job) > 0) {
      log.info("Job {} already completed or decided (idempotent skip).", job);
      jdbc.update("UPDATE scan_jobs SET status='COMPLETED', completed_at=COALESCE(completed_at, CURRENT_TIMESTAMP) WHERE id=?", job);
      return;
    }

    UUID company = (UUID) row.get("company");
    UUID media = (UUID) row.get("media");

    jdbc.update("UPDATE scan_jobs SET status='PROCESSING', started_at=COALESCE(started_at, CURRENT_TIMESTAMP) WHERE id=? AND status IN ('QUEUED','FAILED')", job);

    try {
      // 1. Pipeline: PROVENANCE
      Map<String, Object> provenanceFindings = runProvenancePipeline(company, job, media);

      // 2. Pipeline: RIGHTS & LICENCE
      runRightsAndLicencePipeline(company, job, media);

      // 3. Pipeline: DEPLOYMENT READINESS
      runDeploymentReadinessPipeline(company, job, media, provenanceFindings);

      // 4. Deterministic Decision Engine
      makeDecisionAndReport(company, job, media);

      jdbc.update("UPDATE scan_jobs SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?", job);
      jdbc.update("UPDATE media_assets SET lifecycle_status='AVAILABLE', updated_at=CURRENT_TIMESTAMP WHERE company_id=? AND id=?", company, media);

      audit(company, null, null, "MEDIA_REVIEW_COMPLETED", media, Map.of("jobId", job.toString()));
      log.info("Scan job {} completed successfully for media {}", job, media);
    } catch (RuntimeException e) {
      log.error("Scan job {} failed: {}", job, e.getMessage(), e);
      jdbc.update("UPDATE scan_jobs SET status='FAILED', retry_count=retry_count+1, error_details=? WHERE id=?", error(e), job);
      throw e;
    }
  }

  private Map<String, Object> runProvenancePipeline(UUID company, UUID job, UUID media) {
    UUID runId = UUID.randomUUID();
    jdbc.update("INSERT INTO pipeline_runs (id, company_id, scan_job_id, pipeline_type, status, pipeline_version, started_at) VALUES (?, ?, ?, 'PROVENANCE', 'PROCESSING', 'phase3-2.0', CURRENT_TIMESTAMP)",
        runId, company, job);

    String storageKey = jdbc.queryForObject("SELECT storage_key FROM media_assets WHERE company_id=? AND id=?", String.class, company, media);
    String mimeType = jdbc.queryForObject("SELECT mime_type FROM media_assets WHERE company_id=? AND id=?", String.class, company, media);

    byte[] bytes = null;
    try (InputStream is = storage.retrieve(storageKey)) {
      bytes = is.readAllBytes();
    } catch (Exception e) {
      log.warn("Failed to retrieve media file for provenance: {}", e.getMessage());
    }

    Map<String, Object> summaryResult = new HashMap<>();
    boolean aiServiceSucceeded = false;

    if (bytes != null && bytes.length > 0) {
      try {
        String base64Content = Base64.getEncoder().encodeToString(bytes);
        String requestBody = mapper.writeValueAsString(Map.of(
            "content_base64", base64Content,
            "mime_type", mimeType
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(aiServiceBaseUrl + "/api/v1/analyze/provenance"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(6))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          JsonNode json = mapper.readTree(response.body());
          aiServiceSucceeded = true;

          boolean sigValid = json.path("signature_valid").asBoolean(true);
          String c2paStatus = json.path("c2pa_status").asText("NOT_DETECTED");
          String aiGenStatus = json.path("ai_generation_status").asText("NOT_DETECTED");
          String manipStatus = json.path("manipulation_status").asText("PERFORMED");

          summaryResult.put("aiGenerationStatus", aiGenStatus);
          summaryResult.put("c2paStatus", c2paStatus);
          summaryResult.put("provenanceAvailable", true);

          // Finding 1: Signature & Metadata
          recordFinding(company, runId, "metadata_extraction",
              sigValid ? EvidenceStatus.PASS : EvidenceStatus.FAIL,
              sigValid ? "Image signature and technical metadata verified." : "MIME signature mismatch detected.",
              json(json.path("metadata")));

          // Finding 2: C2PA
          EvidenceStatus c2paEvStatus = "DETECTED".equals(c2paStatus) ? EvidenceStatus.PASS
              : "INVALID".equals(c2paStatus) ? EvidenceStatus.ADVISORY_FLAG
              : "NOT_DETECTED".equals(c2paStatus) ? EvidenceStatus.PASS
              : EvidenceStatus.UNAVAILABLE;
          recordFinding(company, runId, "c2pa_verification", c2paEvStatus,
              "DETECTED".equals(c2paStatus) ? "Valid C2PA content credentials verified."
                  : "INVALID".equals(c2paStatus) ? "Corrupted or invalid C2PA manifest detected."
                  : "No C2PA credentials detected in media.",
              json(json.path("c2pa_details")));

          // Finding 3: Manipulation (ELA)
          JsonNode manip = json.path("manipulation_details");
          String manipIndicator = manip.path("indicator").asText("LOW_DISCREPANCY");
          EvidenceStatus manipEvStatus = "HIGH_DISCREPANCY".equals(manipIndicator) ? EvidenceStatus.ADVISORY_FLAG
              : "UNAVAILABLE".equals(manipIndicator) ? EvidenceStatus.UNAVAILABLE : EvidenceStatus.PASS;
          recordFinding(company, runId, "manipulation_indicators", manipEvStatus,
              manip.path("summary").asText("Compression levels verified."),
              json(manip));

          // Finding 4: AI Generation
          EvidenceStatus aiEvStatus = "DETECTED".equals(aiGenStatus) ? EvidenceStatus.ADVISORY_FLAG
              : "LIKELY_AI_GENERATED".equals(aiGenStatus) ? EvidenceStatus.ADVISORY_FLAG
              : "INCONCLUSIVE".equals(aiGenStatus) ? EvidenceStatus.ADVISORY_FLAG
              : "UNAVAILABLE".equals(aiGenStatus) ? EvidenceStatus.UNAVAILABLE : EvidenceStatus.PASS;
          recordFinding(company, runId, "ai_generation_detection", aiEvStatus,
              json.path("ai_generation_details").path("assessment").asText("Spectral analysis complete."),
              json(json.path("ai_generation_details")));
        }
      } catch (Exception e) {
        log.warn("AI service provenance call failed: {}", e.getMessage());
      }
    }

    if (!aiServiceSucceeded) {
      summaryResult.put("aiGenerationStatus", "UNAVAILABLE");
      summaryResult.put("c2paStatus", "UNAVAILABLE");
      summaryResult.put("provenanceAvailable", false);

      recordFinding(company, runId, "provenance_service_availability", EvidenceStatus.UNAVAILABLE,
          "AI provenance analysis service was unreachable; forensic checks unavailable.",
          json(Map.of("serviceUrl", aiServiceBaseUrl, "status", "UNAVAILABLE")));
    }

    jdbc.update("UPDATE pipeline_runs SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?", runId);
    return summaryResult;
  }

  private void runRightsAndLicencePipeline(UUID company, UUID job, UUID media) {
    UUID runId = UUID.randomUUID();
    jdbc.update("INSERT INTO pipeline_runs (id, company_id, scan_job_id, pipeline_type, status, pipeline_version, started_at) VALUES (?, ?, ?, 'RIGHTS', 'PROCESSING', 'phase3-2.0', CURRENT_TIMESTAMP)",
        runId, company, job);

    String sha256 = jdbc.queryForObject("SELECT sha256_checksum FROM media_assets WHERE company_id=? AND id=?", String.class, company, media);
    String perceptualHash = jdbc.queryForObject("SELECT perceptual_hash FROM media_assets WHERE company_id=? AND id=?", String.class, company, media);

    // Context
    Map<String, Object> ctx = jdbc.queryForMap(
        "SELECT intended_channel, intended_territory, intended_use, planned_publication_at FROM media_assets WHERE company_id=? AND id=?",
        company, media
    );
    String intendedChannel = (String) ctx.get("intended_channel");
    String intendedTerritory = (String) ctx.get("intended_territory");
    String intendedUse = (String) ctx.get("intended_use");
    Instant plannedPublicationAt = ctx.get("planned_publication_at") == null ? null : ((java.sql.Timestamp) ctx.get("planned_publication_at")).toInstant();

    // Query active registered rights assets
    List<Map<String, Object>> candidateAssets = jdbc.query(
        "SELECT id, title, sha256_checksum, perceptual_hash FROM rights_assets WHERE company_id=? AND status='ACTIVE'",
        (rs, n) -> {
          UUID rId = rs.getObject(1, UUID.class);
          String title = rs.getString(2);
          String rSha = rs.getString(3);
          String rPhash = rs.getString(4);

          boolean exact = sha256 != null && sha256.equalsIgnoreCase(rSha);
          int dist = ImageFingerprint.distance(perceptualHash, rPhash);
          double sim = ImageFingerprint.similarity(dist);

          return Map.of(
              "id", rId,
              "title", title != null ? title : "Untitled",
              "exact", exact,
              "distance", dist,
              "similarity", sim
          );
        },
        company
    );

    // Filter matching candidate assets: exact match OR perceptual distance <= 12 (similarity >= 81%)
    List<Map<String, Object>> matches = candidateAssets.stream()
        .filter(c -> Boolean.TRUE.equals(c.get("exact")) || ((Integer) c.get("distance")) <= 12)
        .sorted((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")))
        .toList();

    if (matches.isEmpty()) {
      recordFinding(company, runId, "rights_asset_matching", EvidenceStatus.PASS,
          "No candidate rights matches found in company rights registry.",
          json(Map.of("exactMatches", 0, "perceptualCandidates", 0)));

      recordFinding(company, runId, "licence_applicability", EvidenceStatus.NOT_APPLICABLE,
          "No registered rights assets matched; licence applicability is not required.",
          "{}");
    } else {
      Map<String, Object> topMatch = matches.get(0);
      recordFinding(company, runId, "rights_asset_matching", EvidenceStatus.ADVISORY_FLAG,
          "Potential match identified against registered company asset '" + topMatch.get("title") + "' (similarity: " + (int)(((Double)topMatch.get("similarity"))*100) + "%).",
          json(Map.of("matches", matches)));

      // Query licences associated with matched rights assets
      List<UUID> matchedAssetIds = matches.stream().map(m -> (UUID) m.get("id")).toList();
      List<Map<String, Object>> licences = new ArrayList<>();
      for (UUID aId : matchedAssetIds) {
        licences.addAll(jdbc.query(
            "SELECT l.id, l.licence_reference, l.status, l.valid_from, l.valid_until, l.terms_json::text " +
            "FROM licence_records l " +
            "JOIN licence_rights_assets lra ON lra.company_id=l.company_id AND lra.licence_record_id=l.id " +
            "WHERE l.company_id=? AND lra.rights_asset_id=?",
            (rs, n) -> Map.of(
                "id", rs.getObject(1, UUID.class),
                "reference", rs.getString(2),
                "status", rs.getString(3),
                "validFrom", rs.getTimestamp(4) == null ? Instant.EPOCH : rs.getTimestamp(4).toInstant(),
                "validUntil", rs.getTimestamp(5) == null ? Instant.MAX : rs.getTimestamp(5).toInstant(),
                "termsJson", rs.getString(6) != null ? rs.getString(6) : "{}"
            ),
            company, aId
        ));
      }

      // Evaluate licence applicability
      var licOutcome = LicenceApplicabilityEngine.evaluate(
          matches, licences, intendedChannel, intendedTerritory, intendedUse, plannedPublicationAt
      );

      recordFinding(company, runId, "licence_applicability", licOutcome.status(),
          licOutcome.summary(),
          json(Map.of("violations", licOutcome.violations(), "licences", licOutcome.licenceDetails())));
    }

    jdbc.update("UPDATE pipeline_runs SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?", runId);
  }

  private void runDeploymentReadinessPipeline(UUID company, UUID job, UUID media, Map<String, Object> provenanceFindings) {
    UUID runId = UUID.randomUUID();
    jdbc.update("INSERT INTO pipeline_runs (id, company_id, scan_job_id, pipeline_type, status, pipeline_version, started_at) VALUES (?, ?, ?, 'DEPLOYMENT_READINESS', 'PROCESSING', 'phase3-2.0', CURRENT_TIMESTAMP)",
        runId, company, job);

    Map<String, Object> ctx = jdbc.queryForMap(
        "SELECT intended_channel, intended_territory, intended_use FROM media_assets WHERE company_id=? AND id=?",
        company, media
    );
    String intendedChannel = (String) ctx.get("intended_channel");
    String intendedTerritory = (String) ctx.get("intended_territory");
    String intendedUse = (String) ctx.get("intended_use");

    String rulesJson = jdbc.query(
        "SELECT pv.rules_json::text FROM policy_versions pv " +
        "JOIN company_policies p ON p.id=pv.policy_id " +
        "WHERE pv.company_id=? AND p.status='ACTIVE' AND pv.status='ACTIVE' " +
        "ORDER BY pv.version_number DESC LIMIT 1",
        rs -> rs.next() ? rs.getString(1) : "{}",
        company
    );

    String aiGenStatus = (String) provenanceFindings.getOrDefault("aiGenerationStatus", "UNAVAILABLE");
    String c2paStatus = (String) provenanceFindings.getOrDefault("c2paStatus", "UNAVAILABLE");
    boolean provAvail = (Boolean) provenanceFindings.getOrDefault("provenanceAvailable", false);

    var outcome = PolicyEngine.evaluate(
        rulesJson, intendedChannel, intendedTerritory, intendedUse, aiGenStatus, c2paStatus, provAvail
    );

    for (var rule : outcome.ruleEvaluations()) {
      recordFinding(company, runId, "policy_rule_" + rule.ruleId().toLowerCase(),
          rule.status(), rule.message(), json(rule));
    }

    jdbc.update("UPDATE pipeline_runs SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?", runId);
  }

  private void makeDecisionAndReport(UUID company, UUID job, UUID media) {
    UUID policyVersionId = getOrCreatePolicyVersion(company);

    // Gathers all findings for this job
    List<String> statuses = jdbc.queryForList(
        "SELECT f.status FROM findings f JOIN pipeline_runs p ON p.id=f.pipeline_run_id WHERE f.company_id=? AND p.scan_job_id=?",
        String.class, company, job
    );
    List<EvidenceStatus> evidenceStatuses = statuses.stream().map(EvidenceStatus::valueOf).toList();

    var decisionOutcome = DeterministicDecisionEngine.decideWithReasons(evidenceStatuses);
    PolicyDecision finalDecision = decisionOutcome.decision();
    List<String> reasons = decisionOutcome.reasons();

    UUID decisionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO decisions (id, company_id, scan_job_id, policy_version_id, decision, fired_rule_ids, finding_references, reasons) " +
        "VALUES (?, ?, ?, ?, ?, '[]'::jsonb, '[]'::jsonb, ?::jsonb)",
        decisionId, company, job, policyVersionId, finalDecision.name(), json(reasons)
    );

    // Retrieve original filename for report synthesis
    String filename = jdbc.queryForObject("SELECT original_filename FROM media_assets WHERE company_id=? AND id=?", String.class, company, media);

    // Retrieve findings to pass to AI explanation provider
    List<Finding> allFindings = jdbc.query(
        "SELECT f.id, p.pipeline_type, f.finding_type, f.status, f.summary, COALESCE(f.details_json::text, '{}') " +
        "FROM findings f JOIN pipeline_runs p ON p.id=f.pipeline_run_id WHERE f.company_id=? AND p.scan_job_id=? ORDER BY f.created_at",
        (rs, n) -> new Finding(rs.getObject(1, UUID.class).toString(), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)),
        company, job
    );

    var explanationResult = explanationProvider.generateExplanation(
        finalDecision.name(), filename, allFindings, reasons, "phase3-2.0"
    );

    String reportJson = json(Map.of(
        "mediaId", media.toString(),
        "decision", finalDecision.name(),
        "explanationStatus", explanationResult.explanationStatus(),
        "explanation", explanationResult.explanationText(),
        "providerUsed", explanationResult.providerUsed(),
        "modelUsed", explanationResult.modelUsed(),
        "generatedAt", Instant.now().toString()
    ));

    UUID reportId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO reports (id, company_id, decision_id, report_status, report_json, explanation_status) " +
        "VALUES (?, ?, ?, 'AVAILABLE', ?::jsonb, ?)",
        reportId, company, decisionId, reportJson, explanationResult.explanationStatus()
    );
  }

  private UUID getOrCreatePolicyVersion(UUID company) {
    UUID policyId = jdbc.query("SELECT id FROM company_policies WHERE company_id=? AND name='Default media review policy'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, company);
    if (policyId == null) {
      policyId = UUID.randomUUID();
      jdbc.update("INSERT INTO company_policies (id, company_id, name, status) VALUES (?, ?, 'Default media review policy', 'ACTIVE')", policyId, company);
    }
    UUID versionId = jdbc.query("SELECT id FROM policy_versions WHERE company_id=? AND policy_id=? AND version_number=1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, company, policyId);
    if (versionId == null) {
      versionId = UUID.randomUUID();
      jdbc.update("INSERT INTO policy_versions (id, company_id, policy_id, version_number, status, rules_json, published_at) VALUES (?, ?, ?, 1, 'ACTIVE', '{}'::jsonb, CURRENT_TIMESTAMP)", versionId, company, policyId);
    }
    return versionId;
  }

  private void recordFinding(UUID company, UUID runId, String checkName, EvidenceStatus status, String summary, String detailsJson) {
    UUID findingId = UUID.randomUUID();
    jdbc.update("INSERT INTO findings (id, company_id, pipeline_run_id, finding_type, status, summary, details_json) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
        findingId, company, runId, checkName, status.name(), summary, detailsJson);

    UUID evidenceId = UUID.randomUUID();
    jdbc.update("INSERT INTO evidence_records (id, company_id, pipeline_run_id, finding_id, evidence_type, details_json) VALUES (?, ?, ?, ?, 'ANALYSIS_EVIDENCE', ?::jsonb)",
        evidenceId, company, runId, findingId, detailsJson);
  }

  private void audit(UUID company, UUID user, UUID membership, String eventType, UUID subjectId, Map<String, Object> data) {
    jdbc.update(
        "INSERT INTO audit_events (id, company_id, actor_user_id, actor_membership_id, event_type, subject_type, subject_id, metadata_json) " +
        "VALUES (?, ?, ?, ?, ?, 'MEDIA_REVIEW', ?, ?::jsonb)",
        UUID.randomUUID(), company, user, membership, eventType, subjectId, json(data)
    );
  }

  private String json(Object obj) {
    try {
      return mapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private static String detectMime(byte[] b) {
    if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "image/jpeg";
    if (b.length >= 8 && b[0] == (byte) 137 && b[1] == 80 && b[2] == 78 && b[3] == 71 && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) return "image/png";
    if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
    return "";
  }

  private static Dimension dimensions(byte[] b) {
    try {
      BufferedImage i = ImageIO.read(new ByteArrayInputStream(b));
      return i == null ? new Dimension(null, null) : new Dimension(i.getWidth(), i.getHeight());
    } catch (IOException e) {
      return new Dimension(null, null);
    }
  }

  private static String sha256(byte[] b) {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String extension(String m) {
    return m.equals("image/jpeg") ? "jpg" : m.equals("image/png") ? "png" : "webp";
  }

  private static String safeFilename(String n) {
    if (n == null || n.isBlank()) return "upload";
    String s = n.replace('\\', '/');
    return s.substring(s.lastIndexOf('/') + 1);
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String error(Exception e) {
    String s = e.getMessage();
    return s == null ? e.getClass().getSimpleName() : s.substring(0, Math.min(500, s.length()));
  }

  private static ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }

  private static ResponseStatusException notFound(String s) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, s);
  }

  private record Dimension(Integer width, Integer height) {}
}
