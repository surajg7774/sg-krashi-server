package com.sgkrashi.inquiry.specification;

import com.sgkrashi.inquiry.entity.Inquiry;
import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import org.springframework.data.jpa.domain.Specification;

/** Same one-static-method-per-criterion shape as {@code ProductSpecifications}/{@code OrderSpecifications}. */
public final class InquirySpecifications {

    private InquirySpecifications() {
    }

    public static Specification<Inquiry> hasStatus(InquiryStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Inquiry> hasModuleType(InquiryModuleType moduleType) {
        return (root, query, cb) -> moduleType == null ? null : cb.equal(root.get("moduleType"), moduleType);
    }
}
