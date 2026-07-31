package com.sgkrashi.inquiry.controller;

import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.inquiry.dto.request.AdminInquiryUpdateRequest;
import com.sgkrashi.inquiry.dto.response.AdminInquiryResponse;
import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import com.sgkrashi.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide Inquiry visibility and status management (Module 16) — the first real caller of {@code InquiryService#updateStatus}, built in Module 10. */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminInquiryController {

    private final InquiryService inquiryService;

    public AdminInquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminInquiryResponse>>> list(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryModuleType moduleType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                inquiryService.listInquiriesForAdmin(status, moduleType, page, size), "Inquiries retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminInquiryResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(inquiryService.getInquiryForAdmin(id), "Inquiry retrieved"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminInquiryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminInquiryUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                inquiryService.adminUpdate(id, request.status(), request.adminNotes()), "Inquiry updated"));
    }
}
