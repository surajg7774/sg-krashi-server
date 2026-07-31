package com.sgkrashi.admin.controller;

import com.sgkrashi.admin.dto.request.UpdateUserRolesRequest;
import com.sgkrashi.admin.dto.request.UpdateUserStatusRequest;
import com.sgkrashi.admin.dto.response.AdminUserDetailResponse;
import com.sgkrashi.admin.dto.response.AdminUserResponse;
import com.sgkrashi.admin.service.AdminUserService;
import com.sgkrashi.common.dto.ApiResponse;
import com.sgkrashi.common.dto.PaginatedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.listUsers(search, role, isActive, page, size), "Users retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(id), "User detail retrieved"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.updateStatus(id, request.isActive()), "User status updated"));
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.updateRoles(id, request.roleName(), request.assign()), "User roles updated"));
    }
}
