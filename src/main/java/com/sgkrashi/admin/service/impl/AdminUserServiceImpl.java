package com.sgkrashi.admin.service.impl;

import com.sgkrashi.admin.dto.response.AdminUserDetailResponse;
import com.sgkrashi.admin.dto.response.AdminUserResponse;
import com.sgkrashi.admin.mapper.AdminUserMapper;
import com.sgkrashi.admin.service.AdminUserService;
import com.sgkrashi.admin.specification.UserSpecifications;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.BusinessRuleException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.inquiry.repository.InquiryRepository;
import com.sgkrashi.order.repository.OrderRepository;
import com.sgkrashi.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AdminUserMapper adminUserMapper;

    public AdminUserServiceImpl(
            UserRepository userRepository,
            OrderRepository orderRepository,
            BookingRepository bookingRepository,
            InquiryRepository inquiryRepository,
            ReviewRepository reviewRepository,
            CurrentUserProvider currentUserProvider,
            AdminUserMapper adminUserMapper
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.inquiryRepository = inquiryRepository;
        this.reviewRepository = reviewRepository;
        this.currentUserProvider = currentUserProvider;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public PaginatedResponse<AdminUserResponse> listUsers(String search, String role, Boolean isActive, int page, int size) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.nameOrEmailContains(search),
                UserSpecifications.hasRole(role),
                UserSpecifications.isActive(isActive));

        Page<User> userPage = userRepository.findAll(spec, PageRequest.of(Math.max(page, 0), size > 0 ? size : 20));
        List<AdminUserResponse> items = userPage.getContent().stream().map(adminUserMapper::toSummary).toList();
        return PaginatedResponse.of(items, userPage);
    }

    @Override
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = getUserOrThrow(userId);
        long orderCount = orderRepository.countByUserId(userId);
        long bookingCount = bookingRepository.countByUserId(userId);
        long inquiryCount = inquiryRepository.countByUserId(userId);
        long reviewCount = reviewRepository.countByUserId(userId);
        return adminUserMapper.toDetail(user, orderCount, bookingCount, inquiryCount, reviewCount);
    }

    /**
     * Deactivation is purely a login/access restriction — it flips {@code
     * is_active} on the {@code User} row only. It must never cascade to the
     * user's historical orders/bookings/reviews/etc; nothing here touches
     * any other table, which is itself the guarantee.
     */
    @Override
    @Transactional
    public AdminUserResponse updateStatus(Long userId, boolean isActive) {
        if (!isActive && userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }

        User user = getUserOrThrow(userId);
        user.setActive(isActive);
        User saved = userRepository.save(user);
        return adminUserMapper.toSummary(saved);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
