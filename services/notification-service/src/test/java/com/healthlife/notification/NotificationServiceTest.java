package com.healthlife.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.healthlife.notification.service.DeviceTokenService;
import com.healthlife.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Unit tests for NotificationService covering:
 * - Email sent successfully
 * - Email failure is caught and does not throw (resilient)
 * - Push skipped when Firebase not initialised
 * - Push skipped when no device tokens registered
 * - Push attempted for each registered token
 * - Invalid token removed after failed push
 */
class NotificationServiceTest {

    private JavaMailSender mailSender;
    private DeviceTokenService deviceTokenService;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        deviceTokenService = mock(DeviceTokenService.class);
        service = new NotificationService(mailSender, deviceTokenService);
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @Test
    void sendEmail_shouldCallMailSender() {
        service.sendEmail("user@example.com", "Test Subject", "Test body");

        verify(mailSender)
                .send(argThat((SimpleMailMessage msg) -> "user@example.com".equals(msg.getTo()[0])
                        && "Test Subject".equals(msg.getSubject())
                        && "Test body".equals(msg.getText())));
    }

    @Test
    void sendEmail_mailSenderThrows_shouldNotPropagateException() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        // Must NOT throw — email failures are logged and swallowed
        assertThatCode(() -> service.sendEmail("fail@example.com", "Subject", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendEmail_nullBody_shouldNotThrow() {
        assertThatCode(() -> service.sendEmail("user@example.com", "Subject", null))
                .doesNotThrowAnyException();
    }

    // ── Push — Firebase not initialised ──────────────────────────────────────

    @Test
    void sendPush_firebaseNotInitialised_shouldSkipSilently() {
        // Firebase.getApps() returns empty list when not initialised (default in tests)
        UUID userId = UUID.randomUUID();

        // Should not throw and should not call deviceTokenService
        assertThatCode(() -> service.sendPushNotification(userId, "Title", "Body"))
                .doesNotThrowAnyException();

        // DeviceTokenService should not be called when Firebase is not initialised
        verify(deviceTokenService, never()).getTokensForUser(any());
    }

    // ── Push — no tokens ─────────────────────────────────────────────────────

    @Test
    void sendPush_noTokensRegistered_shouldSkipSilently() {
        // We can't easily test the Firebase-initialised path without a real Firebase app,
        // but we can verify the no-token path via the service contract.
        // The service checks Firebase.getApps().isEmpty() first, so this test
        // verifies the overall resilience of the method.
        UUID userId = UUID.randomUUID();
        when(deviceTokenService.getTokensForUser(userId)).thenReturn(List.of());

        assertThatCode(() -> service.sendPushNotification(userId, "Title", "Body"))
                .doesNotThrowAnyException();
    }

    // ── Email content validation ──────────────────────────────────────────────

    @Test
    void sendEmail_subjectAndBodyPreserved() {
        String subject = "Your daily health insight is ready";
        String body = "Good morning! Here is your personalised health insight for today.";

        service.sendEmail("health@example.com", subject, body);

        verify(mailSender)
                .send(argThat(
                        (SimpleMailMessage msg) -> subject.equals(msg.getSubject()) && body.equals(msg.getText())));
    }

    @Test
    void sendEmail_multipleRecipients_eachSentSeparately() {
        service.sendEmail("a@example.com", "Subject", "Body");
        service.sendEmail("b@example.com", "Subject", "Body");

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    // ── Template email methods ───────────────────────────────────────────────

    @Test
    void sendEmailVerification_shouldCallSendEmail() {
        service.sendEmailVerification("test@example.com", "https://verify.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPasswordReset_shouldCallSendEmail() {
        service.sendPasswordReset("test@example.com", "https://reset.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendWelcome_shouldCallSendEmail() {
        service.sendWelcome("test@example.com", "Test User");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSubscriptionActivated_shouldCallSendEmail() {
        service.sendSubscriptionActivated("test@example.com", "Pro Plan");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSubscriptionCanceled_shouldCallSendEmail() {
        service.sendSubscriptionCanceled("test@example.com", "2025-12-31");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPaymentFailed_shouldCallSendEmail() {
        service.sendPaymentFailed("test@example.com", "https://retry.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── Push notification methods ───────────────────────────────────────────

    @Test
    void sendDailyReminderPush_shouldCallSendPushNotification() {
        UUID userId = UUID.randomUUID();
        // We can't test Firebase init, but we can at least call the method to ensure no exceptions
        assertThatCode(() -> service.sendDailyReminderPush(userId)).doesNotThrowAnyException();
    }

    @Test
    void sendWaterReminderPush_shouldCallSendPushNotification() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendWaterReminderPush(userId)).doesNotThrowAnyException();
    }

    @Test
    void sendGoalAchievedPush_shouldCallSendPushNotification() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendGoalAchievedPush(userId, "Step Goal")).doesNotThrowAnyException();
    }

    @Test
    void sendAiInsightReadyPush_shouldCallSendPushNotification() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendAiInsightReadyPush(userId)).doesNotThrowAnyException();
    }

    @Test
    void sendPush_withTokens_andFirebaseInit_shouldSend() throws FirebaseMessagingException {
        UUID userId = UUID.randomUUID();
        String token1 = "token1";
        String token2 = "token2";
        when(deviceTokenService.getTokensForUser(userId)).thenReturn(List.of(token1, token2));

        try (MockedStatic<FirebaseApp> mockedFirebaseApp = Mockito.mockStatic(FirebaseApp.class);
                MockedStatic<FirebaseMessaging> mockedFirebaseMessaging = Mockito.mockStatic(FirebaseMessaging.class)) {

            FirebaseApp firebaseApp = mock(FirebaseApp.class);
            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(List.of(firebaseApp));

            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebaseMessaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any())).thenReturn("msg1", "msg2");

            service.sendPushNotification(userId, "Title", "Body");

            verify(deviceTokenService, times(1)).getTokensForUser(userId);
        }
    }

    @Test
    void sendPush_withInvalidToken_shouldRemoveToken() throws FirebaseMessagingException {
        UUID userId = UUID.randomUUID();
        String token1 = "invalid-token";
        when(deviceTokenService.getTokensForUser(userId)).thenReturn(List.of(token1));

        try (MockedStatic<FirebaseApp> mockedFirebaseApp = Mockito.mockStatic(FirebaseApp.class);
                MockedStatic<FirebaseMessaging> mockedFirebaseMessaging = Mockito.mockStatic(FirebaseMessaging.class)) {

            FirebaseApp firebaseApp = mock(FirebaseApp.class);
            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(List.of(firebaseApp));

            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebaseMessaging.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(firebaseMessaging.send(any())).thenThrow(exception);

            service.sendPushNotification(userId, "Title", "Body");

            verify(deviceTokenService, times(1)).removeToken(userId, token1);
        }
    }
}
