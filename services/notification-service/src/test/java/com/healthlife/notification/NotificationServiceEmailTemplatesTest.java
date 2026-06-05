package com.healthlife.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.notification.service.DeviceTokenService;
import com.healthlife.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Covers all public NotificationService methods:
 * sendEmailVerification, sendPasswordReset, sendWelcome,
 * sendSubscriptionActivated, sendSubscriptionCanceled, sendPaymentFailed,
 * sendDailyReminderPush, sendWaterReminderPush, sendGoalAchievedPush, sendAiInsightReadyPush
 */
class NotificationServiceEmailTemplatesTest {

    private JavaMailSender mailSender;
    private DeviceTokenService deviceTokenService;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        deviceTokenService = mock(DeviceTokenService.class);
        service = new NotificationService(mailSender, deviceTokenService);
    }

    @Test
    void sendEmailVerification_shouldSendWithCorrectSubject() {
        service.sendEmailVerification("user@test.com", "https://healthlife.com/verify?token=abc");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getSubject() != null && m.getSubject().contains("Verify")
                && m.getTo()[0].equals("user@test.com")));
    }

    @Test
    void sendPasswordReset_shouldSendWithCorrectSubject() {
        service.sendPasswordReset("user@test.com", "https://healthlife.com/reset?token=xyz");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getSubject() != null && m.getTo()[0].equals("user@test.com")));
    }

    @Test
    void sendWelcome_shouldSendEmail() {
        service.sendWelcome("welcome@test.com", "Alice");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getTo()[0].equals("welcome@test.com")));
    }

    @Test
    void sendSubscriptionActivated_shouldSendEmail() {
        service.sendSubscriptionActivated("sub@test.com", "PRO");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getTo()[0].equals("sub@test.com")));
    }

    @Test
    void sendSubscriptionCanceled_shouldSendEmail() {
        service.sendSubscriptionCanceled("cancel@test.com", "2025-12-31");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getTo()[0].equals("cancel@test.com")));
    }

    @Test
    void sendPaymentFailed_shouldSendEmail() {
        service.sendPaymentFailed("fail@test.com", "https://healthlife.com/billing");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getTo()[0].equals("fail@test.com")));
    }

    @Test
    void sendEmailVerification_smtpFailure_shouldNotThrow() {
        doThrow(new RuntimeException("SMTP failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendEmailVerification("u@t.com", "http://url"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendPasswordReset_smtpFailure_shouldNotThrow() {
        doThrow(new RuntimeException("SMTP failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendPasswordReset("u@t.com", "http://url"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendWelcome_smtpFailure_shouldNotThrow() {
        doThrow(new RuntimeException("SMTP failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendWelcome("u@t.com", "Bob"))
                .doesNotThrowAnyException();
    }

    // ── Push notifications — Firebase not initialised ─────────────────────────

    @Test
    void sendDailyReminderPush_firebaseNotInitialised_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendDailyReminderPush(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void sendWaterReminderPush_firebaseNotInitialised_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendWaterReminderPush(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void sendGoalAchievedPush_firebaseNotInitialised_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendGoalAchievedPush(userId, "Daily Steps"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendAiInsightReadyPush_firebaseNotInitialised_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendAiInsightReadyPush(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void sendPushNotification_generic_firebaseNotInitialised_shouldNotThrow() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> service.sendPushNotification(userId, "Title", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendEmail_directCall_shouldSendCorrectMessage() {
        service.sendEmail("direct@test.com", "Direct Subject", "Direct Body");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                "direct@test.com".equals(m.getTo()[0])
                && "Direct Subject".equals(m.getSubject())
                && "Direct Body".equals(m.getText())));
    }
}
