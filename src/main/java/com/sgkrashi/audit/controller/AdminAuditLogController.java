package com.sgkrashi.audit.controller;

import com.sgkrashi.audit.dto.response.AuditLogResponse;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Audit visibility is a Super Admin capability specifically (architecture
 * doc, Section 4 role table) — stricter than every other {@code /admin/*}
 * endpoint in this codebase, which all accept plain ADMIN too. Deliberately
 * {@code hasRole('SUPER_ADMIN')} only, no {@code or hasRole('ADMIN')}.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuditLogController {

    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AuditLogResponse>>> list(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Instant from = dateFrom == null ? null : dateFrom.atStartOfDay(ADMIN_ZONE).toInstant();
        Instant to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant();
        return ResponseEntity.ok(ApiResponse.success(
                auditLogService.listAuditLogs(adminId, entityType, action, from, to, page, size), "Audit logs retrieved"));
    }
}
