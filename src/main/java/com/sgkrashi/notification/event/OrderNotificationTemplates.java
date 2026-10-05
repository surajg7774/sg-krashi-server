package com.sgkrashi.notification.event;

import com.sgkrashi.notification.entity.NotificationType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Every customer-facing order notification (title and body) lives in this one
 * file, so wording is reviewed in one place and a second language is one more
 * bundle in {@link #BUNDLES} — nothing else changes (Item 8, Hindi).
 *
 * <p>{@link Key} also fixes the parts that do not depend on language: which
 * {@link NotificationType} the in-app row gets, and whether the notification is
 * sent as a push. Only "order placed" is in-app only: the customer has just
 * seen it on screen, and the push would arrive while they are still in the
 * app. Email is untouched by this flag (see {@code NotificationService}).
 *
 * <p>Order notifications only. Booking notifications keep their own wording in
 * {@code NotificationEventListener}.
 */
public final class OrderNotificationTemplates {

    public static final String DEFAULT_LANGUAGE = "en";

    public enum Key {
        PLACED(NotificationType.ORDER_PLACED, false),
        CONFIRMED(NotificationType.ORDER_CONFIRMED, true),
        SHIPPED(NotificationType.ORDER_SHIPPED, true),
        DELIVERED(NotificationType.ORDER_DELIVERED, true),
        PAYMENT_FAILED(NotificationType.PAYMENT_FAILED, true),
        REFUNDED(NotificationType.REFUND_PROCESSED, true);

        private final NotificationType type;
        private final boolean push;

        Key(NotificationType type, boolean push) {
            this.type = type;
            this.push = push;
        }
    }

    /** Values a template may interpolate; any may be null when the event does not carry it. */
    public record Params(String orderNumber, BigDecimal amount) {

        public static Params none() {
            return new Params(null, null);
        }
    }

    public record Text(String title, String body) {
    }

    public record Message(NotificationType type, String title, String body, boolean push) {
    }

    private static final Map<String, Map<Key, Function<Params, Text>>> BUNDLES = Map.of(DEFAULT_LANGUAGE, english());

    private OrderNotificationTemplates() {
    }

    public static Message render(Key key, Params params, String language) {
        Map<Key, Function<Params, Text>> bundle = BUNDLES.getOrDefault(language, BUNDLES.get(DEFAULT_LANGUAGE));
        Text text = bundle.get(key).apply(params);
        return new Message(key.type, text.title(), text.body(), key.push);
    }

    private static Map<Key, Function<Params, Text>> english() {
        Map<Key, Function<Params, Text>> m = new EnumMap<>(Key.class);
        m.put(Key.PLACED, p -> new Text("Order Placed",
                "We've received your order " + p.orderNumber() + " for Rs. " + p.amount()
                        + ". Complete payment to confirm it."));
        m.put(Key.CONFIRMED, p -> new Text("Order Confirmed",
                "Your payment was received and your order has been confirmed."));
        m.put(Key.SHIPPED, p -> new Text("Order Shipped",
                "Your order has been shipped and is on its way to you."));
        m.put(Key.DELIVERED, p -> new Text("Order Delivered",
                "Your order has been delivered. We hope you enjoy it — let us know what you think with a review!"));
        m.put(Key.PAYMENT_FAILED, p -> new Text("Payment Failed",
                "Payment for your order could not be processed and the items have been released. "
                        + "Please place a new order to try again."));
        m.put(Key.REFUNDED, p -> new Text("Refund Processed",
                "Your refund of Rs. " + p.amount() + " for your order has been processed."));
        return m;
    }
}
