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
}
