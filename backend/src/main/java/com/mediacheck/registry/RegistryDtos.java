package com.mediacheck.registry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs for Rights Registry endpoints.
 */
public final class RegistryDtos {
    private RegistryDtos() {}

    // ── Rights Asset DTOs ──

    public record CreateAssetRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 300, message = "Title must be at most 300 characters")
        String title,

        @NotBlank(message = "Asset type is required")
        @Size(max = 64, message = "Asset type must be at most 64 characters")
        String assetType,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 200, message = "External reference must be at most 200 characters")
        String externalReference,

        @Size(max = 300, message = "Rights holder must be at most 300 characters")
        String rightsHolder
    ) {}

    public record UpdateAssetRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 300, message = "Title must be at most 300 characters")
        String title,

        @NotBlank(message = "Asset type is required")
        @Size(max = 64, message = "Asset type must be at most 64 characters")
        String assetType,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 200, message = "External reference must be at most 200 characters")
        String externalReference,

        @Size(max = 300, message = "Rights holder must be at most 300 characters")
        String rightsHolder
    ) {}

    public record AssetResponse(
        UUID id,
        String title,
        String assetType,
        String description,
        String externalReference,
        String rightsHolder,
        String status,
        String originalFilename,
        String mimeType,
        Long fileSize,
        String sha256Checksum,
        boolean hasFile,
        UUID createdByMembershipId,
        Instant createdAt,
        Instant updatedAt
    ) {}

    // ── Licence DTOs ──

    public record CreateLicenceRequest(
        @NotBlank(message = "Licence reference is required")
        @Size(max = 200, message = "Licence reference must be at most 200 characters")
        String licenceReference,

        @Size(max = 300, message = "Name must be at most 300 characters")
        String name,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 64, message = "Licence type must be at most 64 characters")
        String licenceType,

        @NotBlank(message = "Licensor name is required")
        @Size(max = 300, message = "Licensor name must be at most 300 characters")
        String licensorName,

        @Size(max = 300, message = "Licensee name must be at most 300 characters")
        String licenseeName,

        Instant validFrom,
        Instant validUntil,
        String status,
        String termsJson
    ) {}

    public record UpdateLicenceRequest(
        @NotBlank(message = "Licence reference is required")
        @Size(max = 200, message = "Licence reference must be at most 200 characters")
        String licenceReference,

        @Size(max = 300, message = "Name must be at most 300 characters")
        String name,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 64, message = "Licence type must be at most 64 characters")
        String licenceType,

        @NotBlank(message = "Licensor name is required")
        @Size(max = 300, message = "Licensor name must be at most 300 characters")
        String licensorName,

        @Size(max = 300, message = "Licensee name must be at most 300 characters")
        String licenseeName,

        Instant validFrom,
        Instant validUntil,
        String termsJson
    ) {}

    public record LicenceResponse(
        UUID id,
        String licenceReference,
        String name,
        String description,
        String licenceType,
        String licensorName,
        String licenseeName,
        Instant validFrom,
        Instant validUntil,
        String status,
        String effectiveStatus,
        String termsJson,
        int assetCount,
        int documentCount,
        UUID createdByMembershipId,
        Instant createdAt,
        Instant updatedAt
    ) {}

    // ── Document DTOs ──

    public record DocumentResponse(
        UUID id,
        String originalFilename,
        String mimeType,
        Long fileSize,
        String sha256Checksum,
        Instant createdAt
    ) {}

    // ── Dashboard ──

    public record DashboardResponse(
        long totalAssets,
        long activeAssets,
        long archivedAssets,
        long totalLicences,
        long activeLicences,
        long expiredLicences,
        long expiringWithin30Days,
        long draftLicences,
        long revokedLicences,
        List<AssetSummary> recentAssets,
        List<LicenceSummary> recentLicences
    ) {}

    public record AssetSummary(UUID id, String title, String assetType, String status, Instant createdAt) {}
    public record LicenceSummary(UUID id, String name, String licenceReference, String status, Instant validUntil, Instant createdAt) {}

    // ── Pagination wrapper ──

    public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}

    // ── Simple message ──

    public record RegistryMessage(String message) {}
}
