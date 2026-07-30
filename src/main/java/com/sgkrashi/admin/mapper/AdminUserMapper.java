package com.sgkrashi.admin.mapper;

import com.sgkrashi.admin.dto.response.AdminUserDetailResponse;
import com.sgkrashi.admin.dto.response.AdminUserResponse;
import com.sgkrashi.auth.entity.Role;
import com.sgkrashi.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AdminUserMapper {

    public AdminUserResponse toSummary(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                roleNames(user),
                user.isActive(),
                user.getCreatedAt()
        );
    }

    public AdminUserDetailResponse toDetail(User user, long orderCount, long bookingCount, long inquiryCount, long reviewCount) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                roleNames(user),
                user.isActive(),
                user.getCreatedAt(),
                orderCount,
                bookingCount,
                inquiryCount,
                reviewCount
        );
    }

    private java.util.List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }
}
