package com.healthlife.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String resetToken) {
        String subject = "Password Reset Request";
        String resetUrl = "https://healthlife.com/reset-password?token=" + resetToken;
        String text = "Click the link to reset your password: " + resetUrl + "\n\nIf you didn't request this, please ignore this email.";

        sendEmail(to, subject, text);
    }

    public void sendEmailVerificationEmail(String to, String verificationToken) {
        String subject = "Email Verification";
        String verificationUrl = "https://healthlife.com/verify-email?token=" + verificationToken;
        String text = "Click the link to verify your email: " + verificationUrl;

        sendEmail(to, subject, text);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}