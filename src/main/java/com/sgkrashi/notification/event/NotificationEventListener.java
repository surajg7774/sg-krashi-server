package com.sgkrashi.notification.event;

import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * One {@code @TransactionalEventListener(phase = AFTER_COMMIT)} method per
 * event. AFTER_COMMIT means these only run once the triggering transaction
 * (checkout, the payment webhook, a booking cancellation, an inquiry status
 * update) has already committed successfully — a listener failing here
 * (e.g. Mailpit unreachable) cannot roll back or delay that transaction,
 * because by the time this runs, that transaction is already closed. See
 * {@code NotificationServiceImpl.notify}'s per-sender try/catch for the
 * complementary guarantee: a sender failure can't undo the notification
 * row this listener asked to be persisted, either.
 */
@Component
public class NotificationEventListener {

    private static final String ORDER_PAYABLE_TYPE = "ORDER";

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.notify(
                event.userId(),
                NotificationType.ORDER_CONFIRMED,
                "Order Confirmed",
                "Your payment was received and your order has been confirmed.",
                NotificationRelatedType.ORDER,
                event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        boolean isOrder = ORDER_PAYABLE_TYPE.equals(event.payableType());
        notificationService.notify(
                event.userId(),
                NotificationType.PAYMENT_FAILED,
                "Payment Failed",
                "Payment for your " + (isOrder ? "order" : "booking") + " could not be processed. Please try again.",
                isOrder ? NotificationRelatedType.ORDER : NotificationRelatedType.BOOKING,
                event.payableId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationService.notify(
                event.userId(),
                NotificationType.BOOKING_CONFIRMED,
                "Booking Confirmed",
                "Your payment was received and your booking has been confirmed.",
                NotificationRelatedType.BOOKING,
                event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        String reasonSuffix = event.reason() != null ? " (" + event.reason() + ")" : "";
        notificationService.notify(
                event.userId(),
                NotificationType.BOOKING_CANCELLED,
                "Booking Cancelled",
                "Your booking has been cancelled" + reasonSuffix + ".",
                NotificationRelatedType.BOOKING,
                event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquiryStatusChanged(InquiryStatusChangedEvent event) {
        // Guest-submitted inquiries have no userId — no User to notify
        // in-app, consistent with Module 10's precedent that guest inquiries
        // never appear in any user-scoped view either.
        if (event.userId() == null) {
            return;
        }
        notificationService.notify(
                event.userId(),
                NotificationType.INQUIRY_STATUS_CHANGED,
                "Inquiry Update",
                "Your inquiry status changed to " + event.newStatus() + ".",
                NotificationRelatedType.INQUIRY,
                event.inquiryId());
    }
}
