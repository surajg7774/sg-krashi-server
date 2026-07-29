package com.sgkrashi.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic pagination envelope mirroring the frontend's {@code Paginated<T>} type
 * (shared/types/api.ts) exactly — used instead of returning a raw Spring Data
 * {@link Page} so the API contract doesn't leak Spring Data's own JSON shape.
 */
public record PaginatedResponse<T>(List<T> items, int page, int pageSize, long totalCount, int totalPages) {

    public static <T> PaginatedResponse<T> of(Page<T> page) {
        return new PaginatedResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PaginatedResponse<T> of(List<T> items, Page<?> pageInfo) {
        return new PaginatedResponse<>(
                items, pageInfo.getNumber(), pageInfo.getSize(), pageInfo.getTotalElements(), pageInfo.getTotalPages());
    }
}
