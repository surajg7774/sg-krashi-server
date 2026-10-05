package com.sgkrashi.notification.event;

import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.event.OrderNotificationTemplates.Key;
import com.sgkrashi.notification.event.OrderNotificationTemplates.Params;
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
 *
 * <p>Order notifications get their wording and push/in-app-only choice from
 * {@link OrderNotificationTemplates}; the other business lines keep their
 * wording here.
 */
@Component
public class NotificationEventListener {

    private static final String ORDER_PAYABLE_TYPE = "ORDER";

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private void notifyOrder(Long userId, Long orderId, Key key, Params params) {
        OrderNotificationTemplates.Message message =
                OrderNotificationTemplates.render(key, params, OrderNotificationTemplates.DEFAULT_LANGUAGE);
        notificationService.notify(
                userId,
                message.type(),
                message.title(),
                message.body(),
                NotificationRelatedType.ORDER,
                orderId,
                message.push());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notifyOrder(event.userId(), event.orderId(), Key.PLACED, new Params(event.orderNumber(), event.totalAmount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notifyOrder(event.userId(), event.orderId(), Key.CONFIRMED, Params.none());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(OrderShippedEvent event) {
        notifyOrder(event.userId(), event.orderId(), Key.SHIPPED, Params.none());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        notifyOrder(event.userId(), event.orderId(), Key.DELIVERED, Params.none());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (ORDER_PAYABLE_TYPE.equals(event.payableType())) {
            notifyOrder(event.userId(), event.payableId(), Key.PAYMENT_FAILED, Params.none());
            return;
        }
        notificationService.notify(
                event.userId(),
                NotificationType.PAYMENT_FAILED,
                "Payment Failed",
                "Payment for your booking could not be processed. Please try again.",
                NotificationRelatedType.BOOKING,
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
    public void onBookingCompleted(BookingCompletedEvent event) {
        notificationService.notify(
                event.userId(),
                NotificationType.BOOKING_COMPLETED,
                "Booking Completed",
                "Hope you enjoyed it! Your booking is now complete — consider leaving a review.",
                NotificationRelatedType.BOOKING,
                event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundProcessed(RefundProcessedEvent event) {
        if (ORDER_PAYABLE_TYPE.equals(event.payableType())) {
            notifyOrder(event.userId(), event.payableId(), Key.REFUNDED, new Params(null, event.amount()));
            return;
        }
        notificationService.notify(
                event.userId(),
                NotificationType.REFUND_PROCESSED,
                "Refund Processed",
                "Your refund of Rs. " + event.amount() + " for your booking has been processed.",
                NotificationRelatedType.BOOKING,
                event.payableId());
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayoutApproved(PayoutApprovedEvent event) {
        notificationService.notify(
                event.farmerId(),
                NotificationType.PAYOUT_APPROVED,
                "Payout Approved",
                "Your payout has been approved and is awaiting bank transfer.",
                NotificationRelatedType.PAYOUT,
                event.payoutId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayoutPaid(PayoutPaidEvent event) {
        notificationService.notify(
                event.farmerId(),
                NotificationType.PAYOUT_PAID,
                "Payout Paid",
                "Your payout has been paid out.",
                NotificationRelatedType.PAYOUT,
                event.payoutId());
    }
}
