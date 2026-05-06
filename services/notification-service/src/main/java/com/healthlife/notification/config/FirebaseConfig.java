package com.healthlife.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Initialises the Firebase Admin SDK on startup.
 *
 * <p>Supply the service-account JSON via the {@code FIREBASE_SERVICE_ACCOUNT_JSON} environment
 * variable (recommended for production — store in Vault / Kubernetes Secret). If the variable is
 * absent the SDK is not initialised and push notifications are silently skipped with a warning log.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    public FirebaseConfig(@Value("${firebase.service-account-json:}") String serviceAccountJson) {
        if (!StringUtils.hasText(serviceAccountJson)) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_JSON is not set — push notifications are disabled. "
                    + "Set the environment variable to enable Firebase Cloud Messaging.");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return; // Already initialised (e.g. in tests)
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
            FirebaseOptions options =
                    FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised successfully");
        } catch (IOException e) {
            log.error("Failed to initialise Firebase Admin SDK: {}", e.getMessage());
        }
    }
}
