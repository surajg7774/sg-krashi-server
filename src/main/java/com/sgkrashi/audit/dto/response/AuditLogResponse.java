package com.sgkrashi.audit.dto.response;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String adminName,
        String adminEmail,
        String action,
        String entityType,
        Long entityId,
        String beforeJson,
        String afterJson,
        String ipAddress,
        Instant createdAt
) {
}
