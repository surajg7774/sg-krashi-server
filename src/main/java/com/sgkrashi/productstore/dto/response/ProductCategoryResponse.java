package com.sgkrashi.productstore.dto.response;

import java.util.List;

/**
 * Category tree node (chosen over a flat list — more directly usable for a
 * category sidebar/filter tree on the frontend without it having to reassemble
 * parent/child relationships itself).
 */
public record ProductCategoryResponse(Long id, String name, String slug, List<ProductCategoryResponse> children) {
}
