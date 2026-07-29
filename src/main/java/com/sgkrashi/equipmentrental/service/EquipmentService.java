package com.sgkrashi.equipmentrental.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;

import java.util.List;

public interface EquipmentService {

    PaginatedResponse<EquipmentSummaryResponse> listEquipment(String category, int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active equipment matches */
    EquipmentDetailResponse getEquipmentDetail(String idOrSlug);

    List<String> listCategories();
}
