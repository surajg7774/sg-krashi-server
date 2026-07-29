package com.sgkrashi.media.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over where uploaded files physically live. Callers depend only on
 * this interface, never on a concrete implementation — swapping
 * {@link LocalStorageProvider} for an S3-compatible provider in production means
 * adding a new {@code @Component} (e.g. behind a {@code @Profile("prod")} or
 * {@code @ConditionalOnProperty}) that uploads to S3 and returns the object's
 * public/CDN URL from {@link #store}, with no changes needed to
 * {@code MediaService} or any controller.
 */
public interface StorageProvider {

    /** Persists the file and returns a URL the frontend can load it from. */
    String store(MultipartFile file);

    /** Removes a previously stored file, given the URL returned by {@link #store}. */
    void delete(String url);
}
