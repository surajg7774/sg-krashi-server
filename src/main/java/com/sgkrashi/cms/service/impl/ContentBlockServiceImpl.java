package com.sgkrashi.cms.service.impl;

import com.sgkrashi.audit.AuditActions;
import com.sgkrashi.audit.service.AuditLogService;
import com.sgkrashi.cms.dto.request.ContentBlockRequest;
import com.sgkrashi.cms.dto.response.ContentBlockResponse;
import com.sgkrashi.cms.entity.ContentBlock;
import com.sgkrashi.cms.entity.ContentBlockType;
import com.sgkrashi.cms.mapper.ContentBlockMapper;
import com.sgkrashi.cms.repository.ContentBlockRepository;
import com.sgkrashi.cms.service.ContentBlockService;
import com.sgkrashi.common.exception.DuplicateResourceException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContentBlockServiceImpl implements ContentBlockService {

    private static final String ENTITY_TYPE_CONTENT_BLOCK = "CONTENT_BLOCK";

    private final ContentBlockRepository contentBlockRepository;
    private final ContentBlockMapper contentBlockMapper;
    private final AuditLogService auditLogService;

    public ContentBlockServiceImpl(
            ContentBlockRepository contentBlockRepository,
            ContentBlockMapper contentBlockMapper,
            AuditLogService auditLogService
    ) {
        this.contentBlockRepository = contentBlockRepository;
        this.contentBlockMapper = contentBlockMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<ContentBlockResponse> listPublic(ContentBlockType type) {
        List<ContentBlock> blocks = type != null
                ? contentBlockRepository.findByTypeAndIsActiveTrueOrderBySortOrderAsc(type)
                : contentBlockRepository.findByIsActiveTrueOrderBySortOrderAsc();
        return blocks.stream().map(contentBlockMapper::toResponse).toList();
    }

    @Override
    public List<ContentBlockResponse> listForAdmin() {
        return contentBlockRepository.findAllByOrderBySortOrderAsc().stream()
                .map(contentBlockMapper::toResponse)
                .toList();
    }

    @Override
    public ContentBlockResponse getForAdmin(Long id) {
        return contentBlockMapper.toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public ContentBlockResponse create(ContentBlockRequest request) {
        if (contentBlockRepository.existsByKey(request.key())) {
            throw new DuplicateResourceException("A content block with key \"" + request.key() + "\" already exists");
        }

        ContentBlock block = new ContentBlock();
        applyRequest(block, request);
        ContentBlock saved = contentBlockRepository.save(block);
        ContentBlockResponse after = contentBlockMapper.toResponse(saved);
        auditLogService.record(AuditActions.CONTENT_BLOCK_CREATED, ENTITY_TYPE_CONTENT_BLOCK, saved.getId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public ContentBlockResponse update(Long id, ContentBlockRequest request) {
        ContentBlock block = getOrThrow(id);
        if (!block.getKey().equals(request.key()) && contentBlockRepository.existsByKey(request.key())) {
            throw new DuplicateResourceException("A content block with key \"" + request.key() + "\" already exists");
        }

        ContentBlockResponse before = contentBlockMapper.toResponse(block);
        applyRequest(block, request);
        ContentBlock saved = contentBlockRepository.save(block);
        ContentBlockResponse after = contentBlockMapper.toResponse(saved);
        auditLogService.record(AuditActions.CONTENT_BLOCK_UPDATED, ENTITY_TYPE_CONTENT_BLOCK, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        ContentBlock block = getOrThrow(id);
        ContentBlockResponse before = contentBlockMapper.toResponse(block);
        block.setActive(false);
        ContentBlock saved = contentBlockRepository.save(block);
        auditLogService.record(AuditActions.CONTENT_BLOCK_DEACTIVATED, ENTITY_TYPE_CONTENT_BLOCK, id, before, contentBlockMapper.toResponse(saved));
    }

    private void applyRequest(ContentBlock block, ContentBlockRequest request) {
        block.setKey(request.key());
        block.setType(request.type());
        block.setPayloadJson(request.payload().toString());
        block.setActive(request.isActive());
        block.setSortOrder(request.sortOrder());
    }

    private ContentBlock getOrThrow(Long id) {
        return contentBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content block not found"));
    }
}
