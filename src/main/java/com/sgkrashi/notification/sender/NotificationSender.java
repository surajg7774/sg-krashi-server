package com.sgkrashi.notification.sender;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.notification.entity.Notification;

/**
 * An EXTERNAL delivery channel for a notification that has already been
 * persisted (see {@code Notification}'s Javadoc — "in-app" is the persisted
 * row itself, not a channel behind this interface). {@link EmailSender} is
 * the only implementation today; adding SMS/WhatsApp later (per the
 * architecture doc's Year 2 plan) is exactly: a new {@code @Component}
 * implementing this interface, nothing else. Spring collects every bean
 * implementing this interface into the {@code List<NotificationSender>}
 * injected into {@code NotificationServiceImpl} — there is no registry to
 * update and no calling code to change.
 */
public interface NotificationSender {

    void send(Notification notification, User user);
}
