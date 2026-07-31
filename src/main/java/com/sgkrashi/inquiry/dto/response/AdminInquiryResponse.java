package com.sgkrashi.inquiry.dto.response;

import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code name}/{@code email}/{@code phone} are the inquiry FORM's own submitted
 * contact fields (always present, even for a guest). {@code userId}/{@code
 * userName}/{@code userEmail} are the linked ACCOUNT's info, present only when
 * the inquirer was logged in at submission time — null for a guest submission.
 */
public record AdminInquiryResponse(
        Long id,
        InquiryModuleType moduleType,
        Long userId,
        String userName,
        String userEmail,
        String name,
        String email,
        String phone,
        String message,
        LocalDate preferredDate,
        Integer groupSize,
        InquiryStatus status,
        String adminNotes,
        Instant createdAt
) {
}
