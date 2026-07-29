package com.sgkrashi.cropmarketplace.service.impl;

import com.sgkrashi.cropmarketplace.dto.response.CropCategoryResponse;
import com.sgkrashi.cropmarketplace.repository.CropCategoryRepository;
import com.sgkrashi.cropmarketplace.service.CropCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CropCategoryServiceImpl implements CropCategoryService {

    private final CropCategoryRepository cropCategoryRepository;

    public CropCategoryServiceImpl(CropCategoryRepository cropCategoryRepository) {
        this.cropCategoryRepository = cropCategoryRepository;
    }

    @Override
    public List<CropCategoryResponse> listCategories() {
        return cropCategoryRepository.findByIsActiveTrue().stream()
                .map(category -> new CropCategoryResponse(category.getId(), category.getName(), category.getSlug()))
                .toList();
    }
}
