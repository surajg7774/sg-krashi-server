package com.sgkrashi.inquiry.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.inquiry.dto.request.CreateInquiryRequest;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.entity.Inquiry;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import com.sgkrashi.inquiry.repository.InquiryRepository;
import com.sgkrashi.inquiry.service.InquiryService;
import com.sgkrashi.notification.event.InquiryStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;

    public InquiryServiceImpl(
            InquiryRepository inquiryRepository,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inquiryRepository = inquiryRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public InquiryResponse submitInquiry(CreateInquiryRequest request) {
        Inquiry inquiry = new Inquiry();
        inquiry.setUserId(currentUserProvider.getCurrentUserIdOrNull());
        inquiry.setModuleType(request.moduleType());
        inquiry.setName(request.name());
        inquiry.setEmail(request.email());
        inquiry.setPhone(request.phone());
        inquiry.setMessage(request.message());
        inquiry.setPreferredDate(request.preferredDate());
        inquiry.setGroupSize(request.groupSize());
        inquiry.setStatus(InquiryStatus.NEW);

        Inquiry saved = inquiryRepository.save(inquiry);
        return toResponse(saved);
    }

    @Override
    public PaginatedResponse<InquiryResponse> getMyInquiries(int page, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        Page<Inquiry> inquiriesPage = inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<InquiryResponse> items = inquiriesPage.getContent().stream().map(this::toResponse).toList();
        return PaginatedResponse.of(items, inquiriesPage);
    }

    @Override
    @Transactional
    public InquiryResponse updateStatus(Long inquiryId, InquiryStatus newStatus) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));

        if (!inquiry.getStatus().canTransitionTo(newStatus)) {
            throw new BusinessRuleException(
                    "Cannot move an inquiry from " + inquiry.getStatus() + " to " + newStatus);
        }

        inquiry.setStatus(newStatus);
        Inquiry saved = inquiryRepository.save(inquiry);
        eventPublisher.publishEvent(new InquiryStatusChangedEvent(saved.getId(), saved.getUserId(), saved.getStatus()));
        return toResponse(saved);
    }

    private InquiryResponse toResponse(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getModuleType(),
                inquiry.getName(),
                inquiry.getEmail(),
                inquiry.getPhone(),
                inquiry.getMessage(),
                inquiry.getPreferredDate(),
                inquiry.getGroupSize(),
                inquiry.getStatus(),
                inquiry.getCreatedAt()
        );
    }
}
