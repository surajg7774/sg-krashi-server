package com.sgkrashi.media.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Dev-only storage: writes files to a local directory on disk, served back out
 * via the {@code /uploads/**} static resource mapping in {@code WebConfig}. Not
 * suitable for production (files don't survive a redeploy and don't scale across
 * multiple app instances) — see {@link StorageProvider} for how this gets swapped.
 *
 * <p>Active whenever {@code storage.provider} is unset or {@code local} — the
 * default, so existing local dev setups need zero config changes. Set
 * {@code STORAGE_PROVIDER=s3} to switch to {@link S3StorageProvider} instead;
 * the two are mutually exclusive by construction (Spring would fail to start
 * with two {@code StorageProvider} beans otherwise), not by convention.
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    private static final String URL_PREFIX = "/uploads/";

    private final Path uploadDir;

    public LocalStorageProvider(@Value("${app.media.upload-dir:uploads}") String uploadDirPath) {
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create upload directory: " + this.uploadDir, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String filename = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        Path target = uploadDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }
        return URL_PREFIX + filename;
    }

    @Override
    public void delete(String url) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete stored file: " + filename, ex);
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
