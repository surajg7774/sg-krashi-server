package com.sgkrashi.media.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Production storage backed by Cloudinary. Cloudinary is <b>not</b>
 * S3-protocol-compatible — despite {@code S3StorageProvider}'s original
 * Javadoc listing it as one, it exposes no {@code PutObject}/{@code
 * GetObject} endpoint an S3 SDK client could point at. This talks to
 * Cloudinary's own REST upload API via its official Java SDK instead,
 * authenticated with {@code cloud_name}/{@code api_key}/{@code api_secret}
 * — a fundamentally different credential shape from S3's access/secret key
 * pair, which is the tell that these two providers were never actually
 * interchangeable. Active only when {@code STORAGE_PROVIDER=cloudinary}.
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryStorageProvider implements StorageProvider {

    private final Cloudinary cloudinary;

    public CloudinaryStorageProvider(
            @Value("${storage.cloudinary.cloud-name}") String cloudName,
            @Value("${storage.cloudinary.api-key}") String apiKey,
            @Value("${storage.cloudinary.api-secret}") String apiSecret
    ) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    @Override
    public String store(MultipartFile file) {
        // public_id is assigned ourselves (matching LocalStorageProvider/
        // S3StorageProvider's own UUID-per-file convention) rather than left
        // to Cloudinary's auto-generated id, so delete() can deterministically
        // recover it from the stored URL with no extra lookup.
        String publicId = UUID.randomUUID().toString();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", false,
                    "unique_filename", false));
            return (String) result.get("secure_url");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }
    }

    /**
     * Media rows created before this provider became active (local {@code
     * /uploads/...} paths, or seeded {@code placehold.co} URLs — confirmed
     * live: most existing product/crop images are still one of these) were
     * never on Cloudinary at all. {@link #extractPublicId} assumes the
     * Cloudinary {@code .../upload/v<ver>/<publicId>.<ext>} shape; fed a URL
     * without {@code /upload/} in it, it slices garbage out of the raw
     * string instead and would call {@code destroy()} on that nonsense
     * id — pointless at best, and a real (if astronomically unlikely)
     * collision risk with an unrelated asset's public_id at worst. Real fix
     * is to recognize this URL was never Cloudinary's problem and skip the
     * API call entirely; the DB row deletion below still removes it from
     * the app.
     */
    private static final String CLOUDINARY_HOST_MARKER = "res.cloudinary.com";

    @Override
    public void delete(String url) {
        if (!url.contains(CLOUDINARY_HOST_MARKER)) {
            return;
        }
        String publicId = extractPublicId(url);
        try {
            // invalidate=true matters here specifically: destroy() alone only
            // removes the asset from Cloudinary's own storage — the delivery
            // URL stays served from CDN edge caches (30-day max-age on these
            // responses) until invalidation is explicitly requested. Without
            // this, a "deleted" image keeps loading at its old URL for weeks.
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true));
            String outcome = (String) result.get("result");
            // "not found" is an acceptable outcome for a delete, not a
            // failure — the end state a delete wants (this public_id doesn't
            // exist on Cloudinary) is already true either way. Only a truly
            // unexpected outcome should fail loudly here.
            if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                throw new IllegalStateException(
                        "Cloudinary reported deletion did not succeed for " + publicId + ": " + outcome);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete stored file: " + publicId, ex);
        }
    }

    /**
     * Cloudinary URLs look like
     * {@code https://res.cloudinary.com/<cloud>/image/upload/v<version>/<publicId>.<ext>}.
     * Reliable here specifically because {@link #store} never uses a folder
     * prefix — the segment right after {@code /upload/v<digits>/}, minus its
     * extension, is always exactly the {@code public_id} we assigned.
     */
    private String extractPublicId(String url) {
        String afterUpload = url.substring(url.indexOf("/upload/") + "/upload/".length());
        String afterVersion = afterUpload.replaceFirst("^v\\d+/", "");
        int dotIndex = afterVersion.lastIndexOf('.');
        return dotIndex >= 0 ? afterVersion.substring(0, dotIndex) : afterVersion;
    }
}
