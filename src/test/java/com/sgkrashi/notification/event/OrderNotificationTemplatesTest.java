package com.sgkrashi.notification.event;

import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.event.OrderNotificationTemplates.Key;
import com.sgkrashi.notification.event.OrderNotificationTemplates.Message;
import com.sgkrashi.notification.event.OrderNotificationTemplates.Params;
import com.sgkrashi.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderNotificationTemplatesTest {

    private static final Params PARAMS = new Params("SGK-7", new BigDecimal("250.00"));

    @Test
    void everyKeyHasAnEnglishTemplate() {
        for (Key key : Key.values()) {
            Message m = OrderNotificationTemplates.render(key, PARAMS, "en");
            assertNotNull(m.type(), key.name());
            assertFalse(m.title().isBlank(), key.name());
            assertFalse(m.body().isBlank(), key.name());
        }
    }

    @Test
    void onlyOrderPlacedIsInAppOnly() {
        for (Key key : Key.values()) {
            boolean push = OrderNotificationTemplates.render(key, PARAMS, "en").push();
            assertEquals(key != Key.PLACED, push, key.name());
        }
    }

    @Test
    void eachStatusMapsToItsOwnNotificationType() {
        assertEquals(NotificationType.ORDER_PLACED, OrderNotificationTemplates.render(Key.PLACED, PARAMS, "en").type());
        assertEquals(NotificationType.ORDER_CONFIRMED, OrderNotificationTemplates.render(Key.CONFIRMED, PARAMS, "en").type());
        assertEquals(NotificationType.ORDER_SHIPPED, OrderNotificationTemplates.render(Key.SHIPPED, PARAMS, "en").type());
        assertEquals(NotificationType.ORDER_DELIVERED, OrderNotificationTemplates.render(Key.DELIVERED, PARAMS, "en").type());
        assertEquals(NotificationType.PAYMENT_FAILED, OrderNotificationTemplates.render(Key.PAYMENT_FAILED, PARAMS, "en").type());
        assertEquals(NotificationType.REFUND_PROCESSED, OrderNotificationTemplates.render(Key.REFUNDED, PARAMS, "en").type());
    }

    @Test
    void anUnknownLanguageFallsBackToEnglish() {
        assertEquals(
                OrderNotificationTemplates.render(Key.SHIPPED, PARAMS, "en"),
                OrderNotificationTemplates.render(Key.SHIPPED, PARAMS, "hi"));
    }

    @Test
    void placedAndRefundedInterpolateTheirValues() {
        assertTrue(OrderNotificationTemplates.render(Key.PLACED, PARAMS, "en").body().contains("SGK-7"));
        assertTrue(OrderNotificationTemplates.render(Key.PLACED, PARAMS, "en").body().contains("250.00"));
        assertTrue(OrderNotificationTemplates.render(Key.REFUNDED, PARAMS, "en").body().contains("250.00"));
    }

    @Test
    void theShippedWordingIsPlainAndHonest() {
        Message m = OrderNotificationTemplates.render(Key.SHIPPED, Params.none(), "en");
        assertEquals("Order Shipped", m.title());
        assertEquals("Your order has been shipped and is on its way to you.", m.body());
    }

    // ---- the listener routes through the templates ------------------------------------------

    @Test
    void theListenerSendsPlacedWithoutPushAndShippedWithPush() {
        NotificationService service = mock(NotificationService.class);
        NotificationEventListener listener = new NotificationEventListener(service);

        listener.onOrderPlaced(new OrderPlacedEvent(7L, 5L, "SGK-7", new BigDecimal("250.00")));
        verify(service).notify(5L, NotificationType.ORDER_PLACED, "Order Placed",
                "We've received your order SGK-7 for Rs. 250.00. Complete payment to confirm it.",
                NotificationRelatedType.ORDER, 7L, false);

        listener.onOrderShipped(new OrderShippedEvent(7L, 5L));
        verify(service).notify(5L, NotificationType.ORDER_SHIPPED, "Order Shipped",
                "Your order has been shipped and is on its way to you.",
                NotificationRelatedType.ORDER, 7L, true);
    }

    @Test
    void bookingNotificationsKeepTheirOwnWording() {
        NotificationService service = mock(NotificationService.class);
        NotificationEventListener listener = new NotificationEventListener(service);

        listener.onPaymentFailed(new PaymentFailedEvent("BOOKING", 9L, 5L));

        verify(service).notify(5L, NotificationType.PAYMENT_FAILED, "Payment Failed",
                "Payment for your booking could not be processed. Please try again.",
                NotificationRelatedType.BOOKING, 9L);
    }
}
