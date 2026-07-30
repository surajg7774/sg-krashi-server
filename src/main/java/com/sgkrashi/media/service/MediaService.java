package com.sgkrashi.media.service;

import com.sgkrashi.media.dto.response.MediaAssetResponse;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    /**
     * Validates and stores an uploaded image, then records it as a
     * {@code MediaAsset} owned by the given type/id.
     *
     * @throws com.sgkrashi.common.exception.ValidationException if the file is missing,
     * too large, or not a recognized image type
     */
    MediaAssetResponse upload(MultipartFile file, String ownerType, Long ownerId);

    /**
     * Module 15 — Admin only. A genuine hard delete, unlike Product/CropListing/
     * Equipment/StayListing's soft-delete convention: nothing else in this
     * codebase references a specific {@code MediaAsset} row the way orders/
     * bookings/reviews reference their catalog entity, so there's no
     * historical-reference reason to keep it around. Also removes the
     * underlying stored file via {@code StorageProvider.delete}.
     */
    void delete(Long id);

    /** Module 15 — Admin only, for the ImageUploader's reorder control. */
    MediaAssetResponse updateSortOrder(Long id, int sortOrder);
}
