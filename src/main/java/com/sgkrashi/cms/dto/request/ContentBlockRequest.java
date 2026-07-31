package com.sgkrashi.cms.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sgkrashi.cms.entity.ContentBlockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code payload} is bound as a {@link JsonNode} rather than a typed class —
 * its real shape depends on {@code type} (see {@code ContentBlockType}'s
 * Javadoc) and is intentionally kept loose on the backend; Jackson still
 * validates it's syntactically valid JSON at binding time.
 */
public record ContentBlockRequest(
        @NotBlank(message = "Key is required")
        String key,

        @NotNull(message = "Type is required")
        ContentBlockType type,

        @NotNull(message = "Payload is required")
        JsonNode payload,

        boolean isActive,

        int sortOrder
) {
}
