package com.sgkrashi.inquiry.dto.request;

import com.sgkrashi.inquiry.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;

/** {@code status} is always required (send the inquiry's current status if only {@code adminNotes} is changing) — see {@code InquiryServiceImpl#adminUpdate}'s Javadoc for why a same-status resubmission is safe. */
public record AdminInquiryUpdateRequest(
        @NotNull(message = "Status is required")
        InquiryStatus status,

        String adminNotes
) {
}
