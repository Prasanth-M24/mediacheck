package com.mediacheck.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mediacheck.config.ServiceProperties;

/**
 * Local filesystem storage implementation.
 * 
 * Directory structure:
 *   {basePath}/{category}/{companyId}/{fileId}.{extension}
 *
 * Storage keys use forward slashes and are relative to basePath.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    private final Path basePath;

    public LocalStorageService(ServiceProperties properties) {
        this.basePath = Paths.get(properties.storage().path()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage directory: " + this.basePath, e);
        }
        log.info("Local storage initialized at {}", this.basePath);
    }

    @Override
    public StoredFile store(String category, UUID companyId, UUID fileId,
                            String extension, InputStream inputStream, long expectedSize) {
        validateCategory(category);
        if (expectedSize < 0) {
            throw new StorageException("File size is required");
        }
        if (expectedSize == 0 || expectedSize > MAX_FILE_SIZE) {
            throw new StorageException("File exceeds maximum size of " + MAX_FILE_SIZE + " bytes");
        }

        String safeExt = sanitizeExtension(extension);
        String storageKey = category + "/" + companyId + "/" + fileId + "." + safeExt;
        Path targetPath = resolveAndValidate(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytesWritten;
            Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp-" + UUID.randomUUID());

            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                bytesWritten = Files.copy(dis, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (bytesWritten == 0 || bytesWritten > MAX_FILE_SIZE || bytesWritten != expectedSize) {
                Files.deleteIfExists(tempPath);
                throw new StorageException("File exceeds maximum size of " + MAX_FILE_SIZE + " bytes");
            }

            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            String mimeType = Files.probeContentType(targetPath);
            if (mimeType == null) {
                mimeType = guessMimeType(safeExt);
            }

            log.info("Stored file: key={}, size={}, checksum={}", storageKey, bytesWritten, checksum);
            return new StoredFile(storageKey, mimeType, bytesWritten, checksum);

        } catch (IOException e) {
            // Clean up on failure
            try { Files.deleteIfExists(targetPath); } catch (IOException ignored) {}
            throw new StorageException("Failed to store file: " + storageKey, e);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path filePath = resolveAndValidate(storageKey);
        if (!Files.exists(filePath)) {
            throw new StorageException("File not found: " + storageKey);
        }
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path filePath = resolveAndValidate(storageKey);
        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted file: key={}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete file: key={}", storageKey, e);
        }
    }

    private Path resolveAndValidate(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException("Invalid storage key: path traversal detected");
        }
        return resolved;
    }

    private void validateCategory(String category) {
        if (category == null || !category.matches("[a-z][a-z0-9-]*")) {
            throw new StorageException("Invalid storage category: " + category);
        }
    }

    private String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) return "bin";
        String clean = extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (clean.isEmpty()) return "bin";
        if (clean.length() > 10) return clean.substring(0, 10);
        return clean;
    }

    private String guessMimeType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
