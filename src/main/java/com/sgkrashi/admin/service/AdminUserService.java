package com.sgkrashi.admin.service;

import com.sgkrashi.admin.dto.response.AdminUserDetailResponse;
import com.sgkrashi.admin.dto.response.AdminUserResponse;
import com.sgkrashi.common.dto.PaginatedResponse;

public interface AdminUserService {

    PaginatedResponse<AdminUserResponse> listUsers(String search, String role, Boolean isActive, int page, int size);

    AdminUserDetailResponse getUserDetail(Long userId);

    AdminUserResponse updateStatus(Long userId, boolean isActive);

    /**
     * Module 20 — grants or revokes {@code roleName} on {@code userId}.
     * Refuses to leave a user with zero roles, and refuses to let the
     * caller revoke ADMIN/SUPER_ADMIN from their own account (self-demotion).
     */
    AdminUserResponse updateRoles(Long userId, String roleName, boolean assign);
}
