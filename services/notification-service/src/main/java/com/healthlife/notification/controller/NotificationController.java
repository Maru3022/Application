package com.healthlife.notification.controller;

import com.healthlife.common.security.SecurityUtils;
import com.healthlife.notification.service.DeviceTokenService;
import com.healthlife.notification.service.NotificationService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Internal notification endpoints. These are NOT exposed to end-users — they are called
 * only by other microservices (via the internal cluster network) or by ADMIN users.
 *
 * <p>Access is restricted to ROLE_ADMIN to prevent email-relay abuse where any authenticated
 * user could send arbitrary emails to arbitrary addresses.
 */

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final DeviceTokenService deviceTokenService;

    @PostMapping("/email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> sendEmail(
            @RequestParam @Email @NotBlank String to,
            @RequestParam @NotBlank String subject,
            @RequestBody @NotBlank String body) {
        notificationService.sendEmail(to, subject, body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/push")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> sendPush(
            @RequestParam @NotNull UUID userId,
            @RequestParam @NotBlank String title,
            @RequestBody @NotBlank String body) {
        notificationService.sendPushNotification(userId, title, body);
        return ResponseEntity.ok().build();
    }

    /**
     * Registers an FCM device token for the currently authenticated user. Called by the mobile
     * app on startup / token refresh.
     */
    @PostMapping("/device-token")
    public ResponseEntity<Void> registerDeviceToken(@RequestParam @NotBlank String fcmToken) {
        UUID userId = SecurityUtils.getCurrentUserId();
        deviceTokenService.registerToken(userId, fcmToken);
        return ResponseEntity.ok().build();
    }

    /** Removes an FCM device token (called on logout or app uninstall). */
    @DeleteMapping("/device-token")
    public ResponseEntity<Void> removeDeviceToken(@RequestParam @NotBlank String fcmToken) {
        UUID userId = SecurityUtils.getCurrentUserId();
        deviceTokenService.removeToken(userId, fcmToken);
        return ResponseEntity.noContent().build();
    }
}
