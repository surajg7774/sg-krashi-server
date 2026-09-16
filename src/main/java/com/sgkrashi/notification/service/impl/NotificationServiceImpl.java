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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationSender> senders;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationMapper notificationMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            List<NotificationSender> senders,
            CurrentUserProvider currentUserProvider,
            NotificationMapper notificationMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.senders = senders;
        this.currentUserProvider = currentUserProvider;
        this.notificationMapper = notificationMapper;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The DB write runs in its own REQUIRES_NEW transaction (via {@code
     * requiresNewTransactionTemplate}, not {@code @Transactional} on this
     * method — every real caller is a {@code @TransactionalEventListener
     * (phase = AFTER_COMMIT)} method (see {@code NotificationEventListener}),
     * and joining the moribund transaction Spring still reports "active" on
     * that thread was the exact Module 13 bug: the email sent successfully —
     * proving the listener ran — while the Notification row silently never
     * persisted, because the failure surfaced only in {@code
     * TransactionSynchronizationUtils}'s own afterCompletion error log,
     * invisible to both the HTTP caller and this class).
     *
     * <p>The sender loop below deliberately runs <em>after</em> that
     * transaction has already committed and returned — not inside it. A
     * sender does a real, possibly-15-second blocking HTTP call; running
     * that while a DB transaction (and its pooled connection) is still open
     * has no upside and a real downside: a slow or interrupted external call
     * in that state is exactly the kind of thing that can quietly disrupt
     * the transaction's own commit/cleanup without ever reaching this
     * method's own try/catch, leaving no exception to log — a silent
     * failure with no evidence, the same class of bug as Module 13's, just
     * surfacing on the sender side instead of the persistence side.
     *
     * <p>Confirmed via Brevo's own dashboard afterward: the specific
     * booking-cancellation email that prompted this change was actually a
     * Brevo-side soft bounce against a mailinator.com test recipient
     * (rapid automated sends to a disposable-mail domain in one test
     * session), not this method failing — so that particular symptom
     * wasn't actually evidence of the bug described above. Left as-is
     * anyway: holding a DB transaction open across a real external HTTP
     * call is a genuine anti-pattern independent of what triggered the
     * investigation, and the fix costs nothing to keep.
     */
    @Override
    public void notify(
            Long userId, NotificationType type, String title, String message,
            NotificationRelatedType relatedType, Long relatedId
    ) {
        PersistedNotification persisted = requiresNewTransactionTemplate.execute(status -> {
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
            return user == null ? null : new PersistedNotification(saved, user);
        });

        if (persisted == null) {
            // Previously a silent return with no log line at all — if the
            // in-app row above somehow has no matching user, that's worth
            // knowing about, not swallowing the same way a sender failure
            // used to almost be.
            log.warn("Notification persisted for userId {} but no matching User was found — no sender notified", userId);
            return;
        }

        // Each sender is isolated: a broken SMTP connection must not undo the
        // in-app row just persisted above, and must not stop the next sender
        // (e.g. a future SmsSender) from still running.
        for (NotificationSender sender : senders) {
            try {
                sender.send(persisted.notification(), persisted.user());
            } catch (Exception ex) {
                log.warn("Notification sender {} failed for notification {}: {}",
                        sender.getClass().getSimpleName(), persisted.notification().getId(), ex.getMessage());
            }
        }
    }

    private record PersistedNotification(Notification notification, User user) {
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
