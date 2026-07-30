package com.sgkrashi.notification.service.impl;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.auth.repository.UserRepository;
import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.common.dto.PaginatedResponse;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.notification.dto.response.NotificationListResponse;
import com.sgkrashi.notification.dto.response.NotificationResponse;
import com.sgkrashi.notification.entity.Notification;
import com.sgkrashi.notification.entity.NotificationRelatedType;
import com.sgkrashi.notification.entity.NotificationType;
import com.sgkrashi.notification.mapper.NotificationMapper;
import com.sgkrashi.notification.repository.NotificationRepository;
import com.sgkrashi.notification.sender.NotificationSender;
import com.sgkrashi.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationSender> senders;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            List<NotificationSender> senders,
            CurrentUserProvider currentUserProvider,
            NotificationMapper notificationMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.senders = senders;
        this.currentUserProvider = currentUserProvider;
        this.notificationMapper = notificationMapper;
    }

    /**
     * REQUIRES_NEW, not the default REQUIRED — this matters specifically
     * because every real caller is a {@code @TransactionalEventListener(phase
     * = AFTER_COMMIT)} method (see {@code NotificationEventListener}). By the
     * time an AFTER_COMMIT listener runs, Spring's {@code
     * TransactionSynchronizationManager} can still report a transaction as
     * "active" on the thread even though the original transaction's
     * Hibernate session has already been closed — REQUIRED would join that
     * moribund transaction and fail with "no transaction is in progress" the
     * moment Hibernate tries to actually flush (this was caught live: the
     * email sent successfully — proving the listener ran — while the
     * Notification row silently never persisted, since the failure surfaced
     * only in {@code TransactionSynchronizationUtils}'s own afterCompletion
     * error log, invisible to both the HTTP caller and this class). REQUIRES_NEW
     * forces a genuinely new transaction with its own session, sidestepping
     * the stale one entirely.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(
            Long userId, NotificationType type, String title, String message,
            NotificationRelatedType relatedType, Long relatedId
    ) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        Notification saved = notificationRepository.save(notification);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        // Each sender is isolated: a broken SMTP connection must not undo the
        // in-app row just persisted above, and must not stop the next sender
        // (e.g. a future SmsSender) from still running.
        for (NotificationSender sender : senders) {
            try {
                sender.send(saved, user);
            } catch (Exception ex) {
                log.warn("Notification sender {} failed for notification {}: {}",
                        sender.getClass().getSimpleName(), saved.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public NotificationListResponse getMyNotifications(int page, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        Page<Notification> notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<NotificationResponse> items = notificationPage.getContent().stream().map(notificationMapper::toResponse).toList();
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        return NotificationListResponse.of(PaginatedResponse.of(items, notificationPage), unreadCount);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        Long userId = currentUserProvider.getCurrentUserId();
        notificationRepository.markAllAsReadForUser(userId);
    }
}
