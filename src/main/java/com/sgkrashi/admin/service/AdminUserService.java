package com.sgkrashi.admin.service;

import com.sgkrashi.admin.dto.response.AdminUserDetailResponse;
import com.sgkrashi.admin.dto.response.AdminUserResponse;
import com.sgkrashi.common.dto.PaginatedResponse;

public interface AdminUserService {

    PaginatedResponse<AdminUserResponse> listUsers(String search, String role, Boolean isActive, int page, int size);

    AdminUserDetailResponse getUserDetail(Long userId);

    AdminUserResponse updateStatus(Long userId, boolean isActive);
}
