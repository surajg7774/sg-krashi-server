package com.sgkrashi.inquiry.service;

import com.sgkrashi.inquiry.dto.request.ContactSubmissionRequest;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;

public interface InquiryService {

    /**
     * Persists a general contact submission ({@code module_type = GENERAL}).
     * No authentication required — guests may submit this.
     */
    InquiryResponse submitContact(ContactSubmissionRequest request);
}
