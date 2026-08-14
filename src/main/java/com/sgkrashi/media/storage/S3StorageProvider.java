package com.sgkrashi.media.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * Production storage: uploads to any genuinely S3-protocol-compatible
 * object store — real AWS S3, DigitalOcean Spaces, Backblaze B2, a
 * self-hosted MinIO, etc. — via {@code S3_ENDPOINT} (blank/unset means real
 * AWS S3). Active only when {@code STORAGE_PROVIDER=s3}; see
 * {@link LocalStorageProvider} for the dev-default counterpart and {@link
 * CloudinaryStorageProvider} for Cloudinary specifically — it is NOT
 * S3-compatible (no S3 endpoint of any kind), so it's a separate provider
 * with its own real API, not just another {@code S3_ENDPOINT} value here.
 *
 * <p>Deliberately does NOT set an object ACL on upload. AWS S3 buckets
 * created since ~2023 default to "Bucket owner enforced" Object Ownership,
 * which outright rejects {@code PutObject} requests carrying an ACL header
 * (a hard {@code AccessControlListNotSupported} error) — and several
 * S3-compatible providers don't implement per-object ACLs identically to
 * AWS either. Public read is expected to be granted at the bucket level (a
 * bucket policy, or the provider's equivalent "make bucket public" setting)
 * instead — see {@code DEPLOYMENT.md} for the exact policy needed. This is
 * also the more portable choice across providers, not just the safer one.
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "s3")
public class S3StorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrlBase;

    public S3StorageProvider(
            @Value("${storage.s3.bucket-name}") String bucketName,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey,
            @Value("${storage.s3.endpoint:}") String endpoint,
            @Value("${storage.s3.public-url-base}") String publicUrlBase
    ) {
        this.bucketName = bucketName;
        // Trailing slash is normalized away here so store() can always just
        // concatenate "/" + key without a caller having to remember whether
        // they configured the base with or without one.
        this.publicUrlBase = publicUrlBase.endsWith("/")
                ? publicUrlBase.substring(0, publicUrlBase.length() - 1)
                : publicUrlBase;

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            // Path-style (https://endpoint/bucket/key) is what nearly every
            // non-AWS S3-compatible provider expects; AWS itself supports
            // both, so this is safe to always set whenever a custom
            // endpoint is in play.
            builder.forcePathStyle(true);
        }

        this.s3Client = builder.build();
    }

    @Override
    public String store(MultipartFile file) {
        String key = UUID.randomUUID() + extensionOf(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }

        return publicUrlBase + "/" + key;
    }

    @Override
    public void delete(String url) {
        String key = url.substring(url.lastIndexOf('/') + 1);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
