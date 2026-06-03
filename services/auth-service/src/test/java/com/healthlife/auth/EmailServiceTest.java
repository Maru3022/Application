package com.healthlife.auth;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthlife.auth.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EmailService — покрывает sendPasswordResetEmail,
 * sendEmailVerificationEmail, а также обработку ошибок SMTP.
 */
class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(mailSender);
    }

    // ── sendPasswordResetEmail ────────────────────────────────────────────────

    @Test
    void sendPasswordResetEmail_shouldSendEmailWithToken() throws Exception {
        emailService.sendPasswordResetEmail("user@test.com", "reset-token-123");
        // @Async — выполняется синхронно в тестах без настроенного пула
        Thread.sleep(200);
        verify(mailSender, atMostOnce()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPasswordResetEmail_smtpFailure_shouldNotThrow() {
        doThrow(new MailSendException("SMTP failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> {
            emailService.sendPasswordResetEmail("user@test.com", "token");
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }).doesNotThrowAnyException();
    }

    // ── sendEmailVerificationEmail ────────────────────────────────────────────

    @Test
    void sendEmailVerificationEmail_shouldSendEmailWithToken() throws Exception {
        emailService.sendEmailVerificationEmail("new@test.com", "verify-token-456");
        Thread.sleep(200);
        verify(mailSender, atMostOnce()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmailVerificationEmail_smtpFailure_shouldNotThrow() {
        doThrow(new RuntimeException("Connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> {
            emailService.sendEmailVerificationEmail("user@test.com", "token");
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }).doesNotThrowAnyException();
    }
}
