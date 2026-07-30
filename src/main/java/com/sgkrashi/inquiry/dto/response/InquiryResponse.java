package com.sgkrashi.inquiry.dto.response;

import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;

import java.time.Instant;
import java.time.LocalDate;

public record InquiryResponse(
        Long id,
        InquiryModuleType moduleType,
        String name,
        String email,
        String phone,
        String message,
        LocalDate preferredDate,
        Integer groupSize,
        InquiryStatus status,
        Instant createdAt
) {
}
