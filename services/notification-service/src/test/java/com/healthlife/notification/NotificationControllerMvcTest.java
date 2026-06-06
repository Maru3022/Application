package com.healthlife.notification;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.notification.service.DeviceTokenService;
import com.healthlife.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for NotificationController covering:
 * - POST /notifications/device-token → 200 (authenticated user)
 * - DELETE /notifications/device-token → 204
 * - POST /notifications/email → 403 for non-ADMIN
 * - POST /notifications/push → 403 for non-ADMIN
 * - 401 without JWT
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    DeviceTokenService deviceTokenService;

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    private String userJwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    private String adminJwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "admin@t.com", "ADMIN");
    }

    // ── POST /api/v1/notifications/device-token ───────────────────────────────

    @Test
    void registerDeviceToken_shouldReturn200() throws Exception {
        doNothing().when(deviceTokenService).registerToken(any(), anyString());

        mockMvc.perform(post("/api/v1/notifications/device-token?fcmToken=fcm_token_123")
                        .header("Authorization", "Bearer " + userJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void registerDeviceToken_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/device-token?fcmToken=fcm_token_123"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void registerDeviceToken_blankToken_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/device-token?fcmToken=")
                        .header("Authorization", "Bearer " + userJwt()))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/v1/notifications/device-token ─────────────────────────────

    @Test
    void removeDeviceToken_shouldReturn204() throws Exception {
        doNothing().when(deviceTokenService).removeToken(any(), anyString());

        mockMvc.perform(delete("/api/v1/notifications/device-token?fcmToken=fcm_token_123")
                        .header("Authorization", "Bearer " + userJwt()))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/notifications/email (ADMIN only) ─────────────────────────

    @Test
    void sendEmail_asAdmin_shouldReturn200() throws Exception {
        doNothing().when(notificationService).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/notifications/email?to=user@test.com&subject=Test")
                        .header("Authorization", "Bearer " + adminJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Email body"))
                .andExpect(status().isOk());
    }

    @Test
    void sendEmail_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/email?to=user@test.com&subject=Test")
                        .header("Authorization", "Bearer " + userJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Email body"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendEmail_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/email?to=user@test.com&subject=Test")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Email body"))
                .andExpect(status().is4xxClientError());
    }

    // ── POST /api/v1/notifications/push (ADMIN only) ──────────────────────────

    @Test
    void sendPush_asAdmin_shouldReturn200() throws Exception {
        doNothing().when(notificationService).sendPushNotification(any(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/notifications/push?userId=" + UUID.randomUUID() + "&title=Test")
                        .header("Authorization", "Bearer " + adminJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Push body"))
                .andExpect(status().isOk());
    }

    @Test
    void sendPush_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/push?userId=" + UUID.randomUUID() + "&title=Test")
                        .header("Authorization", "Bearer " + userJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Push body"))
                .andExpect(status().isForbidden());
    }
}
