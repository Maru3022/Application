package com.healthlife.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.healthlife.notification.template.NotificationTemplates;
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

    // ── Email ─────────────────────────────────────────────────────────────────

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

    /** Sends an email verification link to the user. */
    public void sendEmailVerification(String to, String verificationUrl) {
        sendEmail(
                to,
                NotificationTemplates.SUBJECT_EMAIL_VERIFICATION,
                NotificationTemplates.emailVerification(verificationUrl));
    }

    /** Sends a password reset link to the user. */
    public void sendPasswordReset(String to, String resetUrl) {
        sendEmail(to, NotificationTemplates.SUBJECT_PASSWORD_RESET, NotificationTemplates.passwordReset(resetUrl));
    }

    /** Sends a welcome email after successful registration. */
    public void sendWelcome(String to, String displayName) {
        sendEmail(to, NotificationTemplates.SUBJECT_WELCOME, NotificationTemplates.welcome(displayName));
    }

    /** Notifies the user that their subscription has been activated. */
    public void sendSubscriptionActivated(String to, String planName) {
        sendEmail(
                to,
                NotificationTemplates.SUBJECT_SUBSCRIPTION_ACTIVATED,
                NotificationTemplates.subscriptionActivated(planName));
    }

    /** Notifies the user that their subscription has been canceled. */
    public void sendSubscriptionCanceled(String to, String periodEnd) {
        sendEmail(
                to,
                NotificationTemplates.SUBJECT_SUBSCRIPTION_CANCELED,
                NotificationTemplates.subscriptionCanceled(periodEnd));
    }

    /** Notifies the user that their payment has failed. */
    public void sendPaymentFailed(String to, String retryUrl) {
        sendEmail(to, NotificationTemplates.SUBJECT_PAYMENT_FAILED, NotificationTemplates.paymentFailed(retryUrl));
    }

    // ── Push ──────────────────────────────────────────────────────────────────

    /**
     * Sends a Firebase Cloud Messaging push notification to all registered device tokens for the
     * given user. Silently skips if Firebase is not initialised (no service-account configured).
     */
    public void sendPushNotification(UUID userId, String title, String body) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn(
                    "Firebase not initialised — skipping push for user={}. Set FIREBASE_SERVICE_ACCOUNT_JSON.", userId);
            return;
        }

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
                if (isInvalidToken(e)) {
                    deviceTokenService.removeToken(userId, token);
                }
            }
        }
        log.info("Push sent to {}/{} devices for user={}", sent, tokens.size(), userId);
    }

    /** Sends a daily reminder push notification. */
    public void sendDailyReminderPush(UUID userId) {
        sendPushNotification(
                userId,
                NotificationTemplates.PUSH_TITLE_DAILY_REMINDER,
                NotificationTemplates.PUSH_BODY_DAILY_REMINDER);
    }

    /** Sends a water intake reminder push notification. */
    public void sendWaterReminderPush(UUID userId) {
        sendPushNotification(
                userId,
                NotificationTemplates.PUSH_TITLE_WATER_REMINDER,
                NotificationTemplates.PUSH_BODY_WATER_REMINDER);
    }

    /** Sends a goal-achieved push notification. */
    public void sendGoalAchievedPush(UUID userId, String goalName) {
        sendPushNotification(
                userId,
                NotificationTemplates.PUSH_TITLE_GOAL_ACHIEVED,
                NotificationTemplates.pushBodyGoalAchieved(goalName));
    }

    /** Sends a new AI insight available push notification. */
    public void sendAiInsightReadyPush(UUID userId) {
        sendPushNotification(
                userId, NotificationTemplates.PUSH_TITLE_AI_INSIGHT, NotificationTemplates.PUSH_BODY_AI_INSIGHT);
    }

    private boolean isInvalidToken(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() == null) return false;
        return switch (e.getMessagingErrorCode()) {
            case UNREGISTERED, INVALID_ARGUMENT -> true;
            default -> false;
        };
    }
}
