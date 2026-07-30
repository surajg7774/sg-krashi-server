package com.sgkrashi.equipmentrental.service;

import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.equipmentrental.dto.request.EquipmentAdminRequest;
import com.sgkrashi.equipmentrental.dto.response.EquipmentDetailResponse;
import com.sgkrashi.equipmentrental.dto.response.EquipmentSummaryResponse;

import java.util.List;

public interface EquipmentService {

    PaginatedResponse<EquipmentSummaryResponse> listEquipment(String category, int page, int size);

    /** @throws com.sgkrashi.common.exception.ResourceNotFoundException if no active equipment matches */
    EquipmentDetailResponse getEquipmentDetail(String idOrSlug);

    List<String> listCategories();

    /** Module 15 — Admin only. */
    EquipmentDetailResponse createEquipment(EquipmentAdminRequest request);

    /** Module 15 — Admin only. */
    EquipmentDetailResponse updateEquipment(Long id, EquipmentAdminRequest request);

    /** Module 15 — Admin only. Soft delete (is_active = false). */
    void deactivateEquipment(Long id);

    /** Module 15 — Admin only. Unlike {@link #listEquipment}, does NOT filter by isActive — see {@code ProductService.listProductsForAdmin}'s Javadoc for why. */
    PaginatedResponse<EquipmentSummaryResponse> listEquipmentForAdmin(String search, int page, int size);

    /** Module 15 — Admin only. Unlike {@link #getEquipmentDetail}, does NOT require the equipment to be active. */
    EquipmentDetailResponse getEquipmentForAdmin(Long id);
}
