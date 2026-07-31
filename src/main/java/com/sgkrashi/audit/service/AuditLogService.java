package com.sgkrashi.audit.service;

import com.sgkrashi.audit.dto.response.AuditLogResponse;
import com.sgkrashi.common.dto.PaginatedResponse;

import java.time.Instant;

public interface AuditLogService {

    /**
     * Records one Admin mutation. Called directly (synchronously) from the
     * mutation method itself, immediately after the state change succeeds —
     * see this module's final report for why a direct call was chosen over
     * an {@code ApplicationEventPublisher}-based event, mirroring Module 13's
     * pattern.
     *
     * <p><b>Never throws.</b> A failure to write the audit row (serialization
     * error, DB issue) is logged at ERROR level and swallowed — the calling
     * mutation's success must never depend on this method succeeding. {@code
     * before}/{@code after} are serialized to JSON via Jackson; pass {@code
     * null} for {@code before} on a create action (nothing existed yet).
     */
    void record(String action, String entityType, Long entityId, Object before, Object after);

    PaginatedResponse<AuditLogResponse> listAuditLogs(
            Long adminId, String entityType, String action, Instant dateFrom, Instant dateTo, int page, int size);
}
