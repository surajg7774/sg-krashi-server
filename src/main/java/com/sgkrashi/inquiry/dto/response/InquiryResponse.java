package com.sgkrashi.inquiry.dto.response;

import java.time.Instant;

public record InquiryResponse(Long id, String moduleType, String status, Instant createdAt) {
}
