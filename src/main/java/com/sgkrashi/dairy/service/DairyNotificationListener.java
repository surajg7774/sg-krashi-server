package com.sgkrashi.dairy.service;

import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Turns subscription events into notifications through the existing notification system (in-app, push, email). */
@Component
public class DairyNotificationListener {

    private final NotificationService notificationService;

    public DairyNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DairySubscriptionEvent event) {
        String product = event.productName();
        String date = String.valueOf(event.date());
        switch (event.kind()) {
            case CREATED -> send(event, NotificationType.SUBSCRIPTION_CREATED, "Subscription started",
                    "Your subscription for " + product + " is set up. The first delivery is on " + date + ".");
            case DELIVERY_SKIPPED_OUT_OF_STOCK -> send(event, NotificationType.SUBSCRIPTION_DELIVERY_SKIPPED, "Delivery skipped",
                    "We couldn't deliver " + product + " on " + date + " because it is out of stock. Nothing is due for that day.");
            case DELIVERED -> send(event, NotificationType.SUBSCRIPTION_DELIVERED, "Delivered",
                    "Your " + product + " delivery for " + date + " has been delivered.");
            case DELIVERY_FAILED -> send(event, NotificationType.SUBSCRIPTION_DELIVERY_FAILED, "Delivery not completed",
                    "We couldn't complete your " + product + " delivery for " + date + "."
                            + (event.detail() == null || event.detail().isBlank() ? "" : " Reason: " + event.detail()));
        }
    }

    private void send(DairySubscriptionEvent event, NotificationType type, String title, String message) {
        notificationService.notify(event.userId(), type, title, message, NotificationRelatedType.SUBSCRIPTION, event.subscriptionId());
    }
}
