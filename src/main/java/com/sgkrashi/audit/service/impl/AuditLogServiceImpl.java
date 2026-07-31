package com.sgkrashi.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgkrashi.audit.dto.response.AuditLogResponse;
import com.sgkrashi.audit.entity.AuditLog;
import com.sgkrashi.audit.repository.AuditLogRepository;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.audit.specification.AuditLogSpecifications;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Deliberately has NO {@code @Transactional} of its own and never
     * propagates an exception — see this class's Javadoc on {@link
     * AuditLogService#record}. Callers run inside their own transaction; if
     * this write fails, that transaction is completely unaffected because the
     * failure never leaves this method.
     */
    @Override
    public void record(String action, String entityType, Long entityId, Object before, Object after) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAdminId(currentUserProvider.getCurrentUserId());
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setBeforeJson(before != null ? objectMapper.writeValueAsString(before) : null);
            entry.setAfterJson(after != null ? objectMapper.writeValueAsString(after) : null);
            entry.setIpAddress(resolveIpAddress());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Accountability logging, not correctness — the business mutation
            // that triggered this must already have succeeded by the time
            // this runs. Logged at ERROR (not WARN): an audit write failure
            // is a compliance-relevant gap, worth being loud about even though
            // it must never roll back or fail the real action.
            LOG.error("Failed to write audit log for action={} entityType={} entityId={}", action, entityType, entityId, e);
        }
    }

    private String resolveIpAddress() {
        try {
            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest().getRemoteAddr() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public PaginatedResponse<AuditLogResponse> listAuditLogs(
            Long adminId, String entityType, String action, Instant dateFrom, Instant dateTo, int page, int size
    ) {
        Specification<AuditLog> spec = Specification.allOf(
                AuditLogSpecifications.hasAdminId(adminId),
                AuditLogSpecifications.hasEntityType(entityType),
                AuditLogSpecifications.hasAction(action),
                AuditLogSpecifications.createdAfter(dateFrom),
                AuditLogSpecifications.createdBefore(dateTo));

        Page<AuditLog> logPage = auditLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by("createdAt").descending()));
        List<AuditLog> logs = logPage.getContent();

        List<Long> adminIds = logs.stream().map(AuditLog::getAdminId).distinct().toList();
        Map<Long, User> adminsById = userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<AuditLogResponse> items = logs.stream()
                .map(entry -> {
                    User admin = adminsById.get(entry.getAdminId());
                    return new AuditLogResponse(
                            entry.getId(),
                            entry.getAdminId(),
                            admin != null ? admin.getName() : "Unknown",
                            admin != null ? admin.getEmail() : null,
                            entry.getAction(),
                            entry.getEntityType(),
                            entry.getEntityId(),
                            entry.getBeforeJson(),
                            entry.getAfterJson(),
                            entry.getIpAddress(),
                            entry.getCreatedAt());
                })
                .toList();
        return PaginatedResponse.of(items, logPage);
    }
}
