package com.sgkrashi.inquiry.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.inquiry.dto.request.CreateInquiryRequest;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.ratelimit.InquiryRateLimiter;
import com.sgkrashi.inquiry.service.InquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public inquiry submission path (any {@code moduleType}), plus the
 * authenticated "my inquiries" listing. Admin inquiry management and
 * status-update endpoints are Module 16's job — see {@code
 * InquiryService#updateStatus}, which already exists for that module to call.
 */
@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;
    private final InquiryRateLimiter inquiryRateLimiter;

    public InquiryController(InquiryService inquiryService, InquiryRateLimiter inquiryRateLimiter) {
        this.inquiryService = inquiryService;
        this.inquiryRateLimiter = inquiryRateLimiter;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponse>> submitInquiry(
            @Valid @RequestBody CreateInquiryRequest request,
            HttpServletRequest servletRequest
    ) {
        if (!inquiryRateLimiter.tryConsume(servletRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Too many submissions. Please try again in a few minutes.");
        }

        InquiryResponse response = inquiryService.submitInquiry(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Thanks for reaching out — we'll be in touch soon"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PaginatedResponse<InquiryResponse>>> listMyInquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(inquiryService.getMyInquiries(page, size), "Inquiries retrieved"));
    }
}
