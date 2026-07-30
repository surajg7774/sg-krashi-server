package com.sgkrashi.notification.dto.response;

import com.sgkrashi.common.dto.PaginatedResponse;

import java.util.List;

/**
 * Same shape as {@code PaginatedResponse<NotificationResponse>} plus {@link
 * #unreadCount} — bundled so the bell badge and the dropdown list refresh
 * from a single polled query (see Module 12's {@code ReviewListResponse} for
 * the same reasoning applied to ratings).
 */
public record NotificationListResponse(
        List<NotificationResponse> items,
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        long unreadCount
) {
    public static NotificationListResponse of(PaginatedResponse<NotificationResponse> paginated, long unreadCount) {
        return new NotificationListResponse(
                paginated.items(), paginated.page(), paginated.pageSize(), paginated.totalCount(), paginated.totalPages(), unreadCount);
    }
}
