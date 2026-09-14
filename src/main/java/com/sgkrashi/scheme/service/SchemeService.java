package com.sgkrashi.scheme.service;

import com.sgkrashi.scheme.dto.request.SchemeRequest;
import com.sgkrashi.scheme.dto.response.SchemeResponse;
import com.sgkrashi.scheme.entity.SchemeCategory;

import java.util.List;

public interface SchemeService {

    List<SchemeResponse> listPublic(SchemeCategory category);

    List<SchemeResponse> listForAdmin();

    SchemeResponse getForAdmin(Long id);

    SchemeResponse create(SchemeRequest request);

    SchemeResponse update(Long id, SchemeRequest request);

    void deactivate(Long id);
}
