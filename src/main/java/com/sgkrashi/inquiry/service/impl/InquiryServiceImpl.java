package com.sgkrashi.inquiry.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.inquiry.dto.request.CreateInquiryRequest;
import com.sgkrashi.inquiry.dto.response.AdminInquiryResponse;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.entity.Inquiry;
import com.sgkrashi.inquiry.entity.InquiryModuleType;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import com.sgkrashi.inquiry.repository.InquiryRepository;
import com.sgkrashi.inquiry.service.InquiryService;
import com.sgkrashi.inquiry.specification.InquirySpecifications;
import com.sgkrashi.notification.event.InquiryStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InquiryServiceImpl implements InquiryService {

    private static final String ENTITY_TYPE_INQUIRY = "INQUIRY";

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public InquiryServiceImpl(
            InquiryRepository inquiryRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService
    ) {
        this.inquiryRepository = inquiryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
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

    @Override
    public PaginatedResponse<AdminInquiryResponse> listInquiriesForAdmin(InquiryStatus status, InquiryModuleType moduleType, int page, int size) {
        Specification<Inquiry> spec = Specification.allOf(
                InquirySpecifications.hasStatus(status),
                InquirySpecifications.hasModuleType(moduleType));

        Page<Inquiry> inquiriesPage = inquiryRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), size > 0 ? size : 20, Sort.by("createdAt").descending()));
        List<Inquiry> inquiries = inquiriesPage.getContent();

        List<Long> userIds = inquiries.stream().map(Inquiry::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<AdminInquiryResponse> items = inquiries.stream()
                .map(inquiry -> toAdminResponse(inquiry, usersById.get(inquiry.getUserId())))
                .toList();
        return PaginatedResponse.of(items, inquiriesPage);
    }

    @Override
    public AdminInquiryResponse getInquiryForAdmin(Long inquiryId) {
        Inquiry inquiry = getOrThrow(inquiryId);
        User user = inquiry.getUserId() != null ? userRepository.findById(inquiry.getUserId()).orElse(null) : null;
        return toAdminResponse(inquiry, user);
    }

    @Override
    @Transactional
    public AdminInquiryResponse adminUpdate(Long inquiryId, InquiryStatus newStatus, String adminNotes) {
        Inquiry inquiry = getOrThrow(inquiryId);
        AdminInquiryResponse before = getInquiryForAdmin(inquiryId);
        inquiry.setAdminNotes(adminNotes);
        inquiryRepository.save(inquiry);

        if (newStatus != inquiry.getStatus()) {
            updateStatus(inquiryId, newStatus);
        }
        AdminInquiryResponse after = getInquiryForAdmin(inquiryId);
        auditLogService.record(AuditActions.INQUIRY_STATUS_UPDATED, ENTITY_TYPE_INQUIRY, inquiryId, before, after);
        return after;
    }

    private Inquiry getOrThrow(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));
    }

    private AdminInquiryResponse toAdminResponse(Inquiry inquiry, User user) {
        return new AdminInquiryResponse(
                inquiry.getId(),
                inquiry.getModuleType(),
                inquiry.getUserId(),
                user != null ? user.getName() : null,
                user != null ? user.getEmail() : null,
                inquiry.getName(),
                inquiry.getEmail(),
                inquiry.getPhone(),
                inquiry.getMessage(),
                inquiry.getPreferredDate(),
                inquiry.getGroupSize(),
                inquiry.getStatus(),
                inquiry.getAdminNotes(),
                inquiry.getCreatedAt()
        );
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
