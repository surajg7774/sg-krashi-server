package com.sgkrashi.media.mapper;

import com.sgkrashi.media.dto.response.MediaAssetResponse;
import com.sgkrashi.media.entity.MediaAsset;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetMapper {

    public MediaAssetResponse toResponse(MediaAsset asset) {
        return new MediaAssetResponse(asset.getId(), asset.getUrl(), asset.getAltText(), asset.getSortOrder());
    }
}
