package com.healthlife.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification, password reset).
 *
 * <p>All send methods are {@code @Async} — email delivery failures must NEVER
 * propagate back to the caller and break user-facing flows (registration, etc.).
 * If SMTP is not configured the error is logged and silently swallowed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendPasswordResetEmail(String to, String resetToken) {
        String subject = "HealthLife — Password Reset Request";
        String resetUrl = "https://healthlife.com/reset-password?token=" + resetToken;
        String text = "Click the link below to reset your password:\n\n" + resetUrl
                + "\n\nThis link expires in 1 hour."
                + "\n\nIf you didn't request this, please ignore this email.";
        sendEmail(to, subject, text);
    }

    @Async
    public void sendEmailVerificationEmail(String to, String verificationToken) {
        String subject = "HealthLife — Verify Your Email";
        String verificationUrl = "https://healthlife.com/verify-email?token=" + verificationToken;
        String text = "Welcome to HealthLife! Please verify your email address:\n\n" + verificationUrl
                + "\n\nThis link expires in 24 hours.";
        sendEmail(to, subject, text);
    }

    /**
     * Internal send helper. Errors are caught and logged — never re-thrown.
     * Email delivery is best-effort; it must not block or fail the calling transaction.
     */
    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            // Log but do NOT re-throw — SMTP misconfiguration must not break registration/reset flows.
            log.warn("Failed to send email to {} (subject: {}): {}", to, subject, e.getMessage());
        }
    }
}
