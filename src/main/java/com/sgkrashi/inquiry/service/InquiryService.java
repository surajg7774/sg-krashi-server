package com.sgkrashi.inquiry.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.inquiry.dto.request.CreateInquiryRequest;
import com.sgkrashi.inquiry.dto.response.AdminInquiryResponse;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;

public interface InquiryService {

    /**
     * Persists an inquiry for whatever {@link com.sgkrashi.inquiry.entity.InquiryModuleType}
     * the request names — {@code GENERAL} for Module 3's contact form, {@code
     * ORGANIC_FARMING} for Module 10's visit/wholesale form, etc. No
     * authentication required — guests may submit this; if the caller is
     * logged in, {@code userId} is still resolved server-side, never trusted
     * from the request body.
     */
    InquiryResponse submitInquiry(CreateInquiryRequest request);

    /**
     * The current user's own inquiries, ownership resolved via the JWT
     * principal only (same pattern as {@code BookingService#listMyBookings}).
     * Guest-submitted inquiries (no {@code userId}) never appear here.
     */
    PaginatedResponse<InquiryResponse> getMyInquiries(int page, int size);

    /**
     * Transitions an inquiry to {@code newStatus}, enforcing {@link
     * InquiryStatus#canTransitionTo}. No controller endpoint calls this yet
     * (Module 16's Admin inquiry management is the first caller) — built now
     * so that module only needs to add a thin controller method.
     */
    InquiryResponse updateStatus(Long inquiryId, InquiryStatus newStatus);

    /** Admin-wide listing (Module 16) — every inquiry platform-wide, guest-submitted included. */
    PaginatedResponse<AdminInquiryResponse> listInquiriesForAdmin(InquiryStatus status, InquiryModuleType moduleType, int page, int size);

    AdminInquiryResponse getInquiryForAdmin(Long inquiryId);

    /**
     * Always persists {@code adminNotes}. Only calls the existing {@link
     * #updateStatus} — untouched by this module — when {@code newStatus}
     * actually differs from the inquiry's current status, since {@code
     * InquiryStatus#canTransitionTo} has no self-transition entries and would
     * otherwise reject a same-status resubmission that was only meant to save
     * notes.
     *
     * @throws com.sgkrashi.common.exception.BusinessRuleException if {@code newStatus} is a genuinely invalid transition from the current status
     */
    AdminInquiryResponse adminUpdate(Long inquiryId, InquiryStatus newStatus, String adminNotes);
}
