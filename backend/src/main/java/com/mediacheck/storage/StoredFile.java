package com.mediacheck.storage;

/**
 * Result of a file storage operation.
 */
public record StoredFile(
    String storageKey,
    String mimeType,
    long fileSize,
    String sha256Checksum
) {}
