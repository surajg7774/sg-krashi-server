package com.sgkrashi.notification.service.impl;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.mapper.NotificationMapper;
import com.sgkrashi.notification.repository.NotificationRepository;
import com.sgkrashi.notification.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code sendPush = false} keeps the in-app row and the email, and skips only the push sender. */
class NotificationServicePushFlagTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationSender email = mock(NotificationSender.class);
    private final NotificationSender push = mock(NotificationSender.class);
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(push.isPush()).thenReturn(true);
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        service = new NotificationServiceImpl(notifications, users, List.of(email, push),
                mock(CurrentUserProvider.class), mock(NotificationMapper.class), mock(PlatformTransactionManager.class));
    }

    @Test
    void withoutPushTheInAppRowAndTheEmailStillHappen() {
        service.notify(5L, NotificationType.ORDER_PLACED, "t", "m", NotificationRelatedType.ORDER, 7L, false);

        verify(notifications).save(any(Notification.class));
        verify(email).send(any(), any());
        verify(push, never()).send(any(), any());
    }

    @Test
    void theDefaultStillSendsEverywhere() {
        service.notify(5L, NotificationType.ORDER_CONFIRMED, "t", "m", NotificationRelatedType.ORDER, 7L);

        verify(email).send(any(), any());
        verify(push).send(any(), any());
    }

    @Test
    void theEmailSenderIsNotAPushChannel() {
        assertEquals(false, new NotificationSender() {
            @Override
            public void send(Notification notification, User user) {
            }
        }.isPush());
    }
}
