package com.healthlife.notification.template;

/**
 * Centralised notification templates for all system-generated messages.
 *
 * <p>Using constants instead of inline strings ensures consistency across the codebase
 * and makes it easy to update copy without hunting through service classes.
 */
public final class NotificationTemplates {

    private NotificationTemplates() {}

    // ── Email subjects ────────────────────────────────────────────────────────

    public static final String SUBJECT_EMAIL_VERIFICATION = "Verify your HealthLife email address";
    public static final String SUBJECT_PASSWORD_RESET = "Reset your HealthLife password";
    public static final String SUBJECT_WELCOME = "Welcome to HealthLife!";
    public static final String SUBJECT_SUBSCRIPTION_ACTIVATED = "Your HealthLife Pro subscription is active";
    public static final String SUBJECT_SUBSCRIPTION_CANCELED = "Your HealthLife subscription has been canceled";
    public static final String SUBJECT_PAYMENT_FAILED = "Action required: payment failed";
    public static final String SUBJECT_DAILY_REMINDER = "Your daily health check-in";
    public static final String SUBJECT_WEEKLY_REPORT = "Your weekly health summary";
    public static final String SUBJECT_GOAL_ACHIEVED = "🎉 Goal achieved!";

    // ── Email bodies ──────────────────────────────────────────────────────────

    public static String emailVerification(String verificationUrl) {
        return """
                Welcome to HealthLife!

                Please verify your email address by clicking the link below:

                %s

                This link expires in 24 hours.

                If you did not create a HealthLife account, please ignore this email.

                — The HealthLife Team
                """
                .formatted(verificationUrl);
    }

    public static String passwordReset(String resetUrl) {
        return """
                You requested a password reset for your HealthLife account.

                Click the link below to set a new password:

                %s

                This link expires in 1 hour.

                If you did not request a password reset, please ignore this email.
                Your password will not be changed.

                — The HealthLife Team
                """
                .formatted(resetUrl);
    }

    public static String welcome(String displayName) {
        return """
                Hi %s,

                Welcome to HealthLife! 🎉

                You're all set to start your health journey. Here's what you can do:

                • Track your sleep, water intake, and activity
                • Log your meals and monitor nutrition
                • Chat with your AI Health Coach
                • Set personal health goals

                Get started: https://app.healthlife.com

                — The HealthLife Team
                """
                .formatted(displayName);
    }

    public static String subscriptionActivated(String planName) {
        return """
                Your HealthLife %s subscription is now active!

                You now have access to all premium features:
                • Unlimited AI Coach conversations
                • Advanced analytics and insights
                • Priority support

                Manage your subscription: https://app.healthlife.com/subscription

                — The HealthLife Team
                """
                .formatted(planName);
    }

    public static String subscriptionCanceled(String periodEnd) {
        return """
                Your HealthLife subscription has been canceled.

                You will continue to have access to premium features until %s.
                After that, your account will revert to the free plan.

                Changed your mind? Resubscribe at: https://app.healthlife.com/subscription

                — The HealthLife Team
                """
                .formatted(periodEnd);
    }

    public static String paymentFailed(String retryUrl) {
        return """
                We were unable to process your HealthLife subscription payment.

                Please update your payment method to continue enjoying premium features:

                %s

                If you have questions, contact us at support@healthlife.com.

                — The HealthLife Team
                """
                .formatted(retryUrl);
    }

    public static String dailyReminder(String displayName) {
        return """
                Hi %s,

                Don't forget to log your health data today! 💪

                • Log your meals
                • Track your water intake
                • Record your mood
                • Check in with your AI Coach

                Open HealthLife: https://app.healthlife.com

                — The HealthLife Team
                """
                .formatted(displayName);
    }

    // ── Push notification titles & bodies ────────────────────────────────────

    public static final String PUSH_TITLE_DAILY_REMINDER = "Time for your daily check-in 💪";
    public static final String PUSH_BODY_DAILY_REMINDER = "Log your health data and stay on track with your goals.";

    public static final String PUSH_TITLE_WATER_REMINDER = "Stay hydrated! 💧";
    public static final String PUSH_BODY_WATER_REMINDER =
            "You haven't logged any water today. Remember to stay hydrated!";

    public static final String PUSH_TITLE_GOAL_ACHIEVED = "Goal achieved! 🎉";

    public static String pushBodyGoalAchieved(String goalName) {
        return "Congratulations! You've reached your " + goalName + " goal today.";
    }

    public static final String PUSH_TITLE_AI_INSIGHT = "Your daily health insight is ready 🤖";
    public static final String PUSH_BODY_AI_INSIGHT = "Your AI Coach has a new personalised insight for you.";

    public static final String PUSH_TITLE_WEEKLY_REPORT = "Your weekly health summary 📊";
    public static final String PUSH_BODY_WEEKLY_REPORT = "See how you did this week and get tips for next week.";
}
