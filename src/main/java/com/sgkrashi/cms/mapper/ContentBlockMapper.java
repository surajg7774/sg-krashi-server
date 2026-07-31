package com.sgkrashi.cms.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.cms.dto.response.ContentBlockResponse;
import com.sgkrashi.cms.entity.ContentBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContentBlockMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ContentBlockMapper.class);

    private final ObjectMapper objectMapper;

    public ContentBlockMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A manually-corrupted or legacy-shaped {@code payload_json} must never
     * take down this row's neighbors — parsing is per-row and any failure
     * here degrades to a {@code null} payload (logged) rather than
     * propagating and 500-ing the whole list response.
     */
    public ContentBlockResponse toResponse(ContentBlock block) {
        JsonNode payload = null;
        try {
            payload = objectMapper.readTree(block.getPayloadJson());
        } catch (Exception e) {
            LOG.warn("Content block {} (key={}) has malformed payload_json — returning null payload", block.getId(), block.getKey(), e);
        }

        return new ContentBlockResponse(
                block.getId(),
                block.getKey(),
                block.getType(),
                payload,
                block.isActive(),
                block.getSortOrder(),
                block.getCreatedAt(),
                block.getUpdatedAt());
    }
}
