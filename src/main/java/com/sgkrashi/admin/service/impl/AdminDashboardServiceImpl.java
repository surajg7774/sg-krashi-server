package com.sgkrashi.admin.service.impl;

import com.sgkrashi.admin.dto.response.DashboardSummaryResponse;
import com.sgkrashi.admin.service.AdminDashboardService;
import com.sgkrashi.booking.entity.BookingStatus;
import com.sgkrashi.booking.repository.BookingRepository;
import com.sgkrashi.inquiry.entity.InquiryStatus;
import com.sgkrashi.inquiry.repository.InquiryRepository;
import com.sgkrashi.order.entity.OrderStatus;
import com.sgkrashi.order.repository.OrderRepository;
import com.sgkrashi.payment.entity.PaymentStatus;
import com.sgkrashi.payment.repository.PaymentRepository;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * All KPIs here are plain on-demand aggregate queries against existing
 * tables — no materialized view/warehouse, per the architecture doc's Year 1
 * scale guidance (that's a Module 19+ concern). Every "today/week/month"
 * window is anchored to IST, same {@code Asia/Kolkata} zone {@code
 * BookingServiceImpl} already uses for cancellation cutoffs, so an admin
 * looking at "today's orders" at 11pm IST sees the same "today" a customer
 * placing that order saw.
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final InquiryRepository inquiryRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    public AdminDashboardServiceImpl(
            OrderRepository orderRepository,
            BookingRepository bookingRepository,
            InquiryRepository inquiryRepository,
            ProductRepository productRepository,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.inquiryRepository = inquiryRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(ADMIN_ZONE);
        Instant startOfToday = today.atStartOfDay(ADMIN_ZONE).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant();
        Instant startOfWeek = today.with(DayOfWeek.MONDAY).atStartOfDay(ADMIN_ZONE).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ADMIN_ZONE).toInstant();

        var ordersSummary = new DashboardSummaryResponse.OrdersSummary(
                orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.CONFIRMED, startOfToday, startOfTomorrow),
                orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.PAYMENT_FAILED, startOfToday, startOfTomorrow),
                orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.PENDING_PAYMENT, startOfToday, startOfTomorrow));

        var bookingsSummary = new DashboardSummaryResponse.BookingsSummary(
                bookingRepository.countByStatusAndStartDateGreaterThanEqual(BookingStatus.CONFIRMED, today),
                bookingRepository.countByStatus(BookingStatus.PENDING_PAYMENT),
                bookingRepository.countCompletedByProxy(today));

        var revenueSummary = new DashboardSummaryResponse.RevenueSummary(
                paymentRepository.sumAmountByStatusSince(PaymentStatus.PAID, startOfToday),
                paymentRepository.sumAmountByStatusSince(PaymentStatus.PAID, startOfWeek),
                paymentRepository.sumAmountByStatusSince(PaymentStatus.PAID, startOfMonth));

        long newInquiriesCount = inquiryRepository.countByStatus(InquiryStatus.NEW);
        long lowStockProductCount = productRepository.countByStockQtyLessThanAndIsActiveTrue(LOW_STOCK_THRESHOLD);

        return new DashboardSummaryResponse(ordersSummary, bookingsSummary, newInquiriesCount, lowStockProductCount, revenueSummary);
    }
}
