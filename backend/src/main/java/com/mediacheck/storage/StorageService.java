package com.mediacheck.storage;

import java.io.InputStream;
import java.util.UUID;

/**
 * Abstraction for file storage operations.
 */
public interface StorageService {

    /**
     * Store a file and return the storage result.
     *
     * @param category     logical category (e.g. "rights-assets", "licence-documents")
     * @param companyId    owning company
     * @param fileId       unique file identifier (used as the stored filename base)
     * @param extension    safe file extension (e.g. "pdf", "png")
     * @param inputStream  file content
     * @param expectedSize declared file size; -1 if unknown
     * @return stored file metadata including the storage key and SHA-256 checksum
     */
    StoredFile store(String category, UUID companyId, UUID fileId,
                     String extension, InputStream inputStream, long expectedSize);

    /**
     * Retrieve a stored file.
     *
     * @param storageKey the internal storage key
     * @return an input stream of the file bytes
     * @throws StorageException if the file is not found or cannot be read
     */
    InputStream retrieve(String storageKey);

    /**
     * Delete a stored file.
     *
     * @param storageKey the internal storage key
     */
    void delete(String storageKey);
}
