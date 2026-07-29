package com.sgkrashi.cropmarketplace.service;

import com.sgkrashi.cropmarketplace.dto.response.CropCategoryResponse;

import java.util.List;

public interface CropCategoryService {

    List<CropCategoryResponse> listCategories();
}
