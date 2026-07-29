package com.sgkrashi.inquiry.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.inquiry.dto.request.ContactSubmissionRequest;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.ratelimit.InquiryRateLimiter;
import com.sgkrashi.inquiry.service.InquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public inquiry submission path. Only the general contact form for Module 3 —
 * per-module inquiry types, listing, and status workflow are Module 10's job.
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
    public ResponseEntity<ApiResponse<InquiryResponse>> submitContact(
            @Valid @RequestBody ContactSubmissionRequest request,
            HttpServletRequest servletRequest
    ) {
        if (!inquiryRateLimiter.tryConsume(servletRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Too many submissions. Please try again in a few minutes.");
        }

        InquiryResponse response = inquiryService.submitContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Thanks for reaching out — we'll be in touch soon"));
    }
}
