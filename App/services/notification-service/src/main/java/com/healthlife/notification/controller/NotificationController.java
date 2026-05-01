package com.healthlife.notification.controller;

import com.healthlife.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/email")
    public ResponseEntity<Void> sendEmail(@RequestParam String to,
                                          @RequestParam String subject,
                                          @RequestBody String body) {
        notificationService.sendEmail(to, subject, body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/push")
    public ResponseEntity<Void> sendPush(@RequestParam UUID userId,
                                         @RequestParam String title,
                                         @RequestBody String body) {
        notificationService.sendPushNotification(userId, title, body);
        return ResponseEntity.ok().build();
    }
}
