package com.sgkrashi.inquiry.service.impl;

import com.sgkrashi.inquiry.dto.request.ContactSubmissionRequest;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.entity.Inquiry;
import com.sgkrashi.inquiry.repository.InquiryRepository;
import com.sgkrashi.inquiry.service.InquiryService;
import org.springframework.stereotype.Service;

@Service
public class InquiryServiceImpl implements InquiryService {

    private static final String GENERAL_MODULE_TYPE = "GENERAL";
    private static final String NEW_STATUS = "NEW";

    private final InquiryRepository inquiryRepository;

    public InquiryServiceImpl(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Override
    public InquiryResponse submitContact(ContactSubmissionRequest request) {
        Inquiry inquiry = new Inquiry();
        inquiry.setModuleType(GENERAL_MODULE_TYPE);
        inquiry.setName(request.name());
        inquiry.setEmail(request.email());
        inquiry.setPhone(request.phone());
        inquiry.setMessage(request.message());
        inquiry.setStatus(NEW_STATUS);

        Inquiry saved = inquiryRepository.save(inquiry);

        return new InquiryResponse(saved.getId(), saved.getModuleType(), saved.getStatus(), saved.getCreatedAt());
    }
}
