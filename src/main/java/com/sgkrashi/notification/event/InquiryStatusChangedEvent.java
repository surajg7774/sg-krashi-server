package com.sgkrashi.notification.event;

import com.sgkrashi.inquiry.entity.InquiryStatus;

/**
 * {@code userId} is nullable — a guest-submitted inquiry has none. The
 * listener no-ops for a null userId (see its Javadoc): there is no User to
 * notify in-app, and this is consistent with Module 10's own precedent that
 * guest inquiries never appear in any user-scoped view.
 */
public record InquiryStatusChangedEvent(Long inquiryId, Long userId, InquiryStatus newStatus) {
}
