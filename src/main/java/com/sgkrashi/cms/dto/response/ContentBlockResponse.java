package com.sgkrashi.cms.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.sgkrashi.cms.entity.ContentBlockType;

import java.time.Instant;

/**
 * {@code payload} is {@code null} if the stored {@code payload_json} failed
 * to parse (a manually-corrupted row, or a legacy shape) — see {@code
 * ContentBlockMapper}. The frontend must render a fallback for a null
 * payload rather than assume it's always present.
 */
public record ContentBlockResponse(
        Long id,
        String key,
        ContentBlockType type,
        JsonNode payload,
        boolean isActive,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
