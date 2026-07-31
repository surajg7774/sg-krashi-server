package com.sgkrashi.audit.specification;

import com.sgkrashi.audit.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Same one-static-method-per-criterion shape as {@code ProductSpecifications}/{@code OrderSpecifications}. */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> hasAdminId(Long adminId) {
        return (root, query, cb) -> adminId == null ? null : cb.equal(root.get("adminId"), adminId);
    }

    public static Specification<AuditLog> hasEntityType(String entityType) {
        return (root, query, cb) -> (entityType == null || entityType.isBlank())
                ? null
                : cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> (action == null || action.isBlank())
                ? null
                : cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> createdAfter(Instant dateFrom) {
        return (root, query, cb) -> dateFrom == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom);
    }

    public static Specification<AuditLog> createdBefore(Instant dateTo) {
        return (root, query, cb) -> dateTo == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), dateTo);
    }
}
