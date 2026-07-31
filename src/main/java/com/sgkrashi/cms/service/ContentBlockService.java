package com.sgkrashi.cms.service;

import com.sgkrashi.cms.dto.request.ContentBlockRequest;
import com.sgkrashi.cms.dto.response.ContentBlockResponse;
import com.sgkrashi.cms.entity.ContentBlockType;

import java.util.List;

public interface ContentBlockService {

    /** Public, unauthenticated — active blocks only, optionally filtered by type, ordered by sortOrder. */
    List<ContentBlockResponse> listPublic(ContentBlockType type);

    /** Admin-scoped — every block including inactive ones, so a deactivated block remains manageable. */
    List<ContentBlockResponse> listForAdmin();

    ContentBlockResponse getForAdmin(Long id);

    /** @throws com.sgkrashi.common.exception.DuplicateResourceException if {@code key} is already in use */
    ContentBlockResponse create(ContentBlockRequest request);

    ContentBlockResponse update(Long id, ContentBlockRequest request);

    void deactivate(Long id);
}
