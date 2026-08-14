package com.sgkrashi.notification.sender;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.notification.entity.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Local dev sends through Mailpit (a real SMTP server that catches mail
 * instead of delivering it — see {@code application.yml}'s {@code
 * spring.mail} config and its web UI at http://localhost:8025). This is a
 * genuine SMTP send, not a log statement — it can be verified independently
 * of this application via Mailpit's own API/UI, which is what proves the
 * email path actually works rather than just not crashing.
 *
 * <p>Active whenever {@code app.mail.provider} is {@code smtp} or unset
 * (local dev never sets it). In production this is dead on Railway
 * specifically — Railway blocks outbound SMTP (587 and 2525 both confirmed
 * blocked at the platform level, not a provider-specific issue) — so prod
 * sets {@code MAIL_PROVIDER=brevo-api} to activate {@link BrevoApiEmailSender}
 * instead. Left in place rather than deleted: it's still exactly right for
 * local dev, and would work on any host that doesn't block SMTP.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp", matchIfMissing = true)
public class EmailSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailSender(JavaMailSender mailSender, @Value("${app.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(Notification notification, User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject(notification.getTitle());
        message.setText(notification.getMessage());
        mailSender.send(message);
    }
}
