package com.sgkrashi.notification.sender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.sgkrashi.auth.entity.User;
import com.sgkrashi.notification.entity.DeviceToken;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.repository.DeviceTokenRepository;
import com.sgkrashi.notification.service.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A failed push is logged with the user id and FCM's error code only: never the device token or FCM's message text. */
class FcmNotificationSenderLogLeakTest {

    private static final String DEVICE_TOKEN = "fcm-device-token-SECRET-1234567890";

    @Test
    void aFailedPushDoesNotLogTheDeviceTokenOrTheFcmMessage() throws Exception {
        FirebaseMessaging firebase = mock(FirebaseMessaging.class);
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(failure.getMessage()).thenReturn("Send failed for token " + DEVICE_TOKEN);
        when(firebase.send(any())).thenThrow(failure);

        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setUserId(42L);
        deviceToken.setToken(DEVICE_TOKEN);
        DeviceTokenRepository repository = mock(DeviceTokenRepository.class);
        when(repository.findByUserId(42L)).thenReturn(List.of(deviceToken));

        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        Notification notification = new Notification();
        notification.setTitle("t");
        notification.setMessage("m");
        notification.setType(NotificationType.ORDER_PLACED);

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level original = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.setLevel(Level.ALL);
        root.addAppender(appender);
        try {
            new FcmNotificationSender(firebase, repository, mock(DeviceTokenService.class)).send(notification, user);
        } finally {
            root.detachAppender(appender);
            root.setLevel(original);
        }

        StringBuilder logged = new StringBuilder();
        appender.list.forEach(e -> logged.append(e.getFormattedMessage()).append('\n'));
        assertTrue(logged.toString().contains("FCM send failed for user 42"), "the failure is still logged");
        assertFalse(logged.toString().contains(DEVICE_TOKEN), "the device token must not be logged");
        assertFalse(logged.toString().contains("Send failed for token"), "FCM's message text must not be logged");
    }
}
