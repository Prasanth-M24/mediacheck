package com.mediacheck.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MediaReviewDtos {
  private MediaReviewDtos() {}

  public record MediaSummary(
      UUID id,
      String originalFilename,
      String mimeType,
      long fileSizeBytes,
      String sha256Checksum,
      String lifecycleStatus,
      String scanStatus,
      String decision,
      Instant uploadedAt
  ) {}

  public record MediaDetail(
      UUID id,
      UUID companyId,
      String originalFilename,
      String mimeType,
      long fileSizeBytes,
      String sha256Checksum,
      Integer widthPixels,
      Integer heightPixels,
      String lifecycleStatus,
      String scanStatus,
      String decision,
      String intendedChannel,
      String intendedTerritory,
      String intendedUse,
      Instant plannedPublicationAt,
      Instant uploadedAt,
      Instant updatedAt
  ) {}

  public record PagedMedia(
      List<MediaSummary> content,
      int page,
      int size,
      long totalElements,
      int totalPages
  ) {}

  public record Finding(
      String id,
      String category,
      String checkName,
      String status,
      String summary,
      String details
  ) {}

  public record ReviewReport(
      UUID reportId,
      UUID mediaId,
      String originalFilename,
      String mimeType,
      long fileSizeBytes,
      String sha256Checksum,
      Integer widthPixels,
      Integer heightPixels,
      String intendedChannel,
      String intendedTerritory,
      String intendedUse,
      Instant plannedPublicationAt,
      String processingStatus,
      String decision,
      String policyVersion,
      String explanationStatus,
      String explanation,
      List<String> reasons,
      List<Finding> evidence,
      Instant generatedAt
  ) {}

  public record ReportSummary(
      UUID reportId,
      UUID mediaId,
      String filename,
      String decision,
      String reportStatus,
      Instant createdAt
  ) {}
}
