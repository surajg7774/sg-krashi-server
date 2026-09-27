package com.sgkrashi.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Only active when {@code app.fcm.enabled=true} — {@link FcmNotificationSender}
 * (and this class) are entirely absent from the context otherwise, so an
 * unset/blank {@code FIREBASE_SERVICE_ACCOUNT_JSON} can never crash the app
 * at boot the way an unguarded required {@code @Value} would (see
 * RAZORPAY_WEBHOOK_SECRET's history: a required property with no default
 * failed the *entire* app's startup, not just the payment feature, the first
 * time this class of mistake happened on this project). Until a real
 * Firebase project exists and {@code FCM_ENABLED=true} is set on Railway,
 * this bean is simply never created and push notifications are silently
 * absent — email notifications are unaffected either way.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String FIREBASE_APP_NAME = "sgkrashi-fcm";

    @Bean
    public FirebaseMessaging firebaseMessaging(@Value("${app.fcm.service-account-json}") String serviceAccountJson) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();

        // Named (not the default) app — this Spring bean owns its own
        // FirebaseApp instance rather than assuming/clobbering a
        // process-wide default one; initializeApp throws if an app with
        // this name already exists (e.g. a hot class-reload in dev), so
        // that case reuses the existing instance instead of failing boot.
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(a -> FIREBASE_APP_NAME.equals(a.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, FIREBASE_APP_NAME));

        log.info("Firebase Admin SDK initialized for FCM push notifications");
        return FirebaseMessaging.getInstance(app);
    }
}
