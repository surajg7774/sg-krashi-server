package com.sgkrashi.notification.sender;

import com.sgkrashi.auth.entity.User;
import com.sgkrashi.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Sends email over Brevo's HTTPS Transactional Email API instead of raw SMTP.
 * Exists specifically because Railway blocks outbound SMTP at the platform
 * level — confirmed live: identical "Couldn't connect to host" failures on
 * both port 587 and Brevo's documented 2525 fallback. HTTPS/443 is unaffected
 * (Gemini, Cloudinary, and Razorpay already prove that from this same
 * deployment), so this reaches the same Brevo account through its REST API
 * instead of an SMTP socket. Active only when {@code app.mail.provider=brevo-api}
 * (see {@link EmailSender}'s Javadoc for the other side of this switch).
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "brevo-api")
public class BrevoApiEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoApiEmailSender.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String SEND_PATH = "/v3/smtp/email";

    private final WebClient webClient;
    private final String apiKey;
    private final String fromAddress;

    public BrevoApiEmailSender(
            @Value("${app.mail.brevo.api-key}") String apiKey,
            @Value("${app.mail.from-address}") String fromAddress,
            @Value("${app.mail.brevo.base-url:https://api.brevo.com}") String baseUrl
    ) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void send(Notification notification, User user) {
        EmailRequest payload = new EmailRequest(
                new Sender(fromAddress, fromAddress),
                List.of(new Recipient(user.getEmail(), user.getName())),
                notification.getTitle(),
                notification.getMessage());

        try {
            // api-key is Brevo's own header-based auth scheme for this API —
            // not Bearer/Basic, and a different credential from the SMTP key
            // used by EmailSender (generated separately in the Brevo dashboard
            // under account icon -> SMTP & API -> API Keys tab).
            webClient.post()
                    .uri(SEND_PATH)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("Brevo API returned {} {}: {}", ex.getStatusCode(), ex.getStatusText(),
                    ex.getResponseBodyAsString());
            throw new IllegalStateException("Brevo API returned an error response", ex);
        } catch (WebClientRequestException ex) {
            log.warn("Brevo API unreachable: {}", ex.getMessage());
            throw new IllegalStateException("Brevo API is unreachable", ex);
        }
    }

    private record EmailRequest(Sender sender, List<Recipient> to, String subject, String textContent) {
    }

    private record Sender(String name, String email) {
    }

    private record Recipient(String email, String name) {
    }
}
