package com.healthlife.notification;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.notification.template.NotificationTemplates;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NotificationTemplates covering:
 * - All email templates contain required content
 * - Push notification titles/bodies are non-blank
 * - Template methods handle edge case inputs
 */
class NotificationTemplatesTest {

    // ── email templates ───────────────────────────────────────────────────────

    @Test
    void emailVerification_shouldContainUrl() {
        String url = "https://healthlife.com/verify?token=abc123";
        String body = NotificationTemplates.emailVerification(url);

        assertThat(body).contains(url);
        assertThat(body).isNotBlank();
    }

    @Test
    void passwordReset_shouldContainUrl() {
        String url = "https://healthlife.com/reset?token=xyz789";
        String body = NotificationTemplates.passwordReset(url);

        assertThat(body).contains(url);
        assertThat(body).isNotBlank();
    }

    @Test
    void welcome_shouldContainDisplayName() {
        String body = NotificationTemplates.welcome("Alice");

        assertThat(body).contains("Alice");
        assertThat(body).isNotBlank();
    }

    @Test
    void subscriptionActivated_shouldContainPlanName() {
        String body = NotificationTemplates.subscriptionActivated("PRO");

        assertThat(body).contains("PRO");
        assertThat(body).isNotBlank();
    }

    @Test
    void subscriptionCanceled_shouldContainPeriodEnd() {
        String body = NotificationTemplates.subscriptionCanceled("2025-12-31");

        assertThat(body).contains("2025-12-31");
        assertThat(body).isNotBlank();
    }

    @Test
    void paymentFailed_shouldContainRetryUrl() {
        String url = "https://healthlife.com/billing/retry";
        String body = NotificationTemplates.paymentFailed(url);

        assertThat(body).contains(url);
        assertThat(body).isNotBlank();
    }

    // ── push notification constants ───────────────────────────────────────────

    @Test
    void pushTitles_shouldBeNonBlank() {
        assertThat(NotificationTemplates.PUSH_TITLE_DAILY_REMINDER).isNotBlank();
        assertThat(NotificationTemplates.PUSH_TITLE_WATER_REMINDER).isNotBlank();
        assertThat(NotificationTemplates.PUSH_TITLE_GOAL_ACHIEVED).isNotBlank();
        assertThat(NotificationTemplates.PUSH_TITLE_AI_INSIGHT).isNotBlank();
    }

    @Test
    void pushBodies_shouldBeNonBlank() {
        assertThat(NotificationTemplates.PUSH_BODY_DAILY_REMINDER).isNotBlank();
        assertThat(NotificationTemplates.PUSH_BODY_WATER_REMINDER).isNotBlank();
        assertThat(NotificationTemplates.PUSH_BODY_AI_INSIGHT).isNotBlank();
    }

    @Test
    void pushBodyGoalAchieved_shouldContainGoalName() {
        String body = NotificationTemplates.pushBodyGoalAchieved("Daily Steps");

        assertThat(body).contains("Daily Steps");
        assertThat(body).isNotBlank();
    }

    // ── email subjects ────────────────────────────────────────────────────────

    @Test
    void emailSubjects_shouldBeNonBlank() {
        assertThat(NotificationTemplates.SUBJECT_EMAIL_VERIFICATION).isNotBlank();
        assertThat(NotificationTemplates.SUBJECT_PASSWORD_RESET).isNotBlank();
        assertThat(NotificationTemplates.SUBJECT_WELCOME).isNotBlank();
        assertThat(NotificationTemplates.SUBJECT_SUBSCRIPTION_ACTIVATED).isNotBlank();
        assertThat(NotificationTemplates.SUBJECT_SUBSCRIPTION_CANCELED).isNotBlank();
        assertThat(NotificationTemplates.SUBJECT_PAYMENT_FAILED).isNotBlank();
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void welcome_emptyName_shouldNotThrow() {
        assertThatCode(() -> NotificationTemplates.welcome(""))
                .doesNotThrowAnyException();
    }

    @Test
    void pushBodyGoalAchieved_emptyGoalName_shouldNotThrow() {
        assertThatCode(() -> NotificationTemplates.pushBodyGoalAchieved(""))
                .doesNotThrowAnyException();
    }
}
