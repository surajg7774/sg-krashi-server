package com.sgkrashi.notification.sender;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.notification.entity.DeviceToken;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.repository.DeviceTokenRepository;
import com.sgkrashi.notification.service.DeviceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The push channel — one FCM send per registered device token for this
 * user (a user can be logged in on several devices at once; see {@code
 * DeviceToken}'s Javadoc). Only active when {@code app.fcm.enabled=true}
 * (see {@code FirebaseConfig}) — the {@code FirebaseMessaging} bean this
 * depends on simply doesn't exist otherwise, so Spring never creates this
 * component either, and email notifications keep working exactly as before
 * with zero code path affected.
 *
 * <p>Collected into {@code NotificationServiceImpl}'s {@code
 * List<NotificationSender>} alongside whichever email sender is active —
 * nothing about the existing event-publishing/notify() call sites changes;
 * every event that already reaches {@code NotificationService.notify} now
 * also reaches this sender for free.
 */
@Component
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FcmNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FcmNotificationSender.class);

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;

    public FcmNotificationSender(
            FirebaseMessaging firebaseMessaging,
            DeviceTokenRepository deviceTokenRepository,
            DeviceTokenService deviceTokenService
    ) {
        this.firebaseMessaging = firebaseMessaging;
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
    }

    @Override
    public boolean isPush() {
        return true;
    }

    @Override
    public void send(Notification notification, User user) {
        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(user.getId());
        for (DeviceToken deviceToken : tokens) {
            sendToToken(notification, deviceToken);
        }
    }

    private void sendToToken(Notification notification, DeviceToken deviceToken) {
        // Data-only fields alongside the display notification — the mobile
        // app's notification-tap handler reads these to deep-link (e.g.
        // relatedType=ORDER, relatedId=123 -> Order History), the same
        // relatedType/relatedId already stored on the Notification row
        // itself and returned by GET /notifications/my.
        Message message = Message.builder()
                .setToken(deviceToken.getToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build())
                .putData("notificationId", String.valueOf(notification.getId()))
                .putData("type", notification.getType().name())
                .putData("relatedType", notification.getRelatedType() != null ? notification.getRelatedType().name() : "")
                .putData("relatedId", notification.getRelatedId() != null ? String.valueOf(notification.getRelatedId()) : "")
                .build();

        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException ex) {
            if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                // The device uninstalled the app, cleared data, or the token
                // otherwise rotated without this app hearing about it — FCM's
                // own signal that the token is permanently dead, not a
                // transient failure. Pruning it here keeps every future send
                // for this user from wasting a call on it again.
                //
                // Goes through DeviceTokenService (its own @Transactional),
                // not deviceTokenRepository directly — this whole sender runs
                // AFTER NotificationServiceImpl's own transaction has already
                // committed (see that class's Javadoc on why), so there is no
                // ambient transaction here for a derived delete query to join;
                // confirmed live, first attempt threw "No EntityManager with
                // actual transaction available for current thread."
                log.info("Pruning dead FCM token for user {}: {}", deviceToken.getUserId(), ex.getMessagingErrorCode());
                deviceTokenService.unregister(deviceToken.getToken());
            } else {
                log.warn("FCM send failed for user {}: {}", deviceToken.getUserId(), ex.getMessagingErrorCode());
            }
        }
    }
}
