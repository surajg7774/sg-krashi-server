package com.sgkrashi.scheme.service.impl;

import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.scheme.dto.request.SchemeRequest;
import com.sgkrashi.scheme.dto.response.SchemeResponse;
import com.sgkrashi.scheme.entity.Scheme;
import com.sgkrashi.scheme.entity.SchemeCategory;
import com.sgkrashi.scheme.repository.SchemeRepository;
import com.sgkrashi.scheme.service.SchemeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchemeServiceImpl implements SchemeService {

    private final SchemeRepository schemeRepository;

    public SchemeServiceImpl(SchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    @Override
    public List<SchemeResponse> listPublic(SchemeCategory category) {
        List<Scheme> schemes = category != null
                ? schemeRepository.findByIsActiveTrueAndCategoryOrderBySortOrderAsc(category)
                : schemeRepository.findByIsActiveTrueOrderBySortOrderAsc();
        return schemes.stream().map(this::toResponse).toList();
    }

    @Override
    public List<SchemeResponse> listForAdmin() {
        return schemeRepository.findAllByOrderBySortOrderAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public SchemeResponse getForAdmin(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public SchemeResponse create(SchemeRequest request) {
        Scheme scheme = new Scheme();
        applyRequest(scheme, request);
        return toResponse(schemeRepository.save(scheme));
    }

    @Override
    @Transactional
    public SchemeResponse update(Long id, SchemeRequest request) {
        Scheme scheme = getOrThrow(id);
        applyRequest(scheme, request);
        return toResponse(schemeRepository.save(scheme));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Scheme scheme = getOrThrow(id);
        scheme.setActive(false);
        schemeRepository.save(scheme);
    }

    private void applyRequest(Scheme scheme, SchemeRequest request) {
        scheme.setName(request.name());
        scheme.setCategory(request.category());
        scheme.setDescription(request.description());
        scheme.setEligibility(request.eligibility());
        scheme.setBenefit(request.benefit());
        scheme.setOfficialLink(request.officialLink());
        scheme.setStateScope(request.stateScope());
        scheme.setSortOrder(request.sortOrder());
        scheme.setActive(request.isActive());
    }

    private Scheme getOrThrow(Long id) {
        return schemeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scheme not found"));
    }

    private SchemeResponse toResponse(Scheme scheme) {
        return new SchemeResponse(
                scheme.getId(), scheme.getName(), scheme.getCategory(), scheme.getDescription(),
                scheme.getEligibility(), scheme.getBenefit(), scheme.getOfficialLink(),
                scheme.getStateScope(), scheme.getSortOrder(), scheme.isActive());
    }
}
