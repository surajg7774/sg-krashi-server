package com.sgkrashi.media.service.impl;

import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.common.exception.ValidationException;
import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import com.sgkrashi.media.mapper.MediaAssetMapper;
import com.sgkrashi.media.repository.MediaAssetRepository;
import com.sgkrashi.media.service.MediaService;
import com.sgkrashi.media.storage.StorageProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
public class MediaServiceImpl implements MediaService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final StorageProvider storageProvider;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetMapper mediaAssetMapper;

    public MediaServiceImpl(
            StorageProvider storageProvider,
            MediaAssetRepository mediaAssetRepository,
            MediaAssetMapper mediaAssetMapper
    ) {
        this.storageProvider = storageProvider;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @Override
    public MediaAssetResponse upload(MultipartFile file, String ownerType, Long ownerId) {
        validate(file);

        String url = storageProvider.store(file);

        MediaAsset asset = new MediaAsset();
        asset.setOwnerType(ownerType);
        asset.setOwnerId(ownerId);
        asset.setUrl(url);
        asset.setSortOrder(0);

        MediaAsset saved = mediaAssetRepository.save(asset);
        return mediaAssetMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));
        mediaAssetRepository.delete(asset);
        storageProvider.delete(asset.getUrl());
    }

    @Override
    @Transactional
    public MediaAssetResponse updateSortOrder(Long id, int sortOrder) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));
        asset.setSortOrder(sortOrder);
        MediaAsset saved = mediaAssetRepository.save(asset);
        return mediaAssetMapper.toResponse(saved);
    }

    /**
     * Never trusts the client-supplied Content-Type header alone — it also reads
     * the file's actual magic bytes and requires the two to agree, so a
     * relabeled non-image file (e.g. a script renamed to "photo.jpg" with a
     * spoofed Content-Type) is rejected rather than stored.
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("A file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("File exceeds the maximum allowed size of 5MB");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            throw new ValidationException("Only JPEG, PNG, and WEBP images are allowed");
        }

        String detectedContentType = detectImageContentType(file);
        if (detectedContentType == null || !detectedContentType.equals(declaredContentType)) {
            throw new ValidationException("File content does not match its declared image type");
        }
    }

    private String detectImageContentType(MultipartFile file) {
        byte[] header = new byte[12];
        int bytesRead;
        try {
            bytesRead = file.getInputStream().read(header);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded file", ex);
        }

        if (bytesRead >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytesRead >= 4
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "image/png";
        }
        if (bytesRead >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
