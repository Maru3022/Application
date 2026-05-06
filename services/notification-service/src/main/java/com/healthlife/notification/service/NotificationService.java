package com.healthlife.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final DeviceTokenService deviceTokenService;

    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Sends a Firebase Cloud Messaging push notification to all registered device tokens for the
     * given user. Silently skips if Firebase is not initialised (no service-account configured).
     *
     * @param userId the target user
     * @param title  notification title
     * @param body   notification body text
     */
    public void sendPushNotification(UUID userId, String title, String body) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn(
                    "Firebase not initialised — skipping push notification for user={}. "
                            + "Set FIREBASE_SERVICE_ACCOUNT_JSON to enable.",
                    userId);
            return;
        }

        // Retrieve all FCM tokens registered for this user (one per device)
        var tokens = deviceTokenService.getTokensForUser(userId);
        if (tokens.isEmpty()) {
            log.debug("No device tokens registered for user={}", userId);
            return;
        }

        Notification notification =
                Notification.builder().setTitle(title).setBody(body).build();

        int sent = 0;
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(notification)
                        .build();
                String messageId = FirebaseMessaging.getInstance().send(message);
                log.debug("Push sent to user={} token={} messageId={}", userId, token, messageId);
                sent++;
            } catch (FirebaseMessagingException e) {
                log.warn(
                        "Failed to send push to user={} token={}: {} ({})",
                        userId,
                        token,
                        e.getMessage(),
                        e.getMessagingErrorCode());
                // If the token is invalid/unregistered, remove it to avoid future failures
                if (isInvalidToken(e)) {
                    deviceTokenService.removeToken(userId, token);
                }
            }
        }
        log.info("Push notification sent to {}/{} devices for user={}", sent, tokens.size(), userId);
    }

    private boolean isInvalidToken(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() == null) return false;
        return switch (e.getMessagingErrorCode()) {
            case UNREGISTERED, INVALID_ARGUMENT -> true;
            default -> false;
        };
    }
}
