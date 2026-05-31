package com.healthlife.user;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.user.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.user.entity.UserGoal;
import com.healthlife.user.entity.UserProfile;
import com.healthlife.user.repository.UserGoalRepository;
import com.healthlife.user.repository.UserProfileRepository;
import com.healthlife.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Critical unit + integration tests for UserService.
 * Covers: profile CRUD, goals CRUD, GDPR export, subscription, soft-delete, isolation.
 */
@SpringBootTest(classes = UserServiceApplication.class)
@ActiveProfiles("test")
class UserServiceCriticalTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserGoalRepository userGoalRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        setAuth(userId, "test@health.com");
        userGoalRepository.deleteAll();
        userProfileRepository.deleteAll();
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void getProfile_existingUser_shouldReturn() {
        saveProfile(userId, "Alice", "FREE");

        UserProfileResponse resp = userService.getProfile();

        assertThat(resp.getDisplayName()).isEqualTo("Alice");
        assertThat(resp.getSubscriptionPlan()).isEqualTo("FREE");
    }

    @Test
    void getProfile_noProfile_shouldThrowNotFound() {
        assertThatThrownBy(() -> userService.getProfile())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_shouldPersistChanges() {
        saveProfile(userId, "Bob", "FREE");

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .displayName("Bobby")
                .timezone("Europe/Moscow")
                .gender("male")
                .heightCm(new BigDecimal("180.5"))
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();

        UserProfileResponse resp = userService.updateProfile(req);

        assertThat(resp.getDisplayName()).isEqualTo("Bobby");
        assertThat(resp.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(resp.getGender()).isEqualTo("male");
        assertThat(resp.getHeightCm()).isEqualByComparingTo(new BigDecimal("180.5"));
    }

    @Test
    void updateProfile_partialUpdate_shouldNotOverwriteNullFields() {
        saveProfile(userId, "Carol", "PRO");

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .displayName("Caroline")
                .build();

        UserProfileResponse resp = userService.updateProfile(req);

        assertThat(resp.getDisplayName()).isEqualTo("Caroline");
        // timezone was not in request — should remain unchanged
        UserProfile saved = userProfileRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getSubscriptionPlan()).isEqualTo("PRO");
    }

    @Test
    void updateProfile_noProfile_shouldThrowNotFound() {
        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .displayName("Ghost")
                .build();

        assertThatThrownBy(() -> userService.updateProfile(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_shouldSetDeletedAt() {
        saveProfile(userId, "Dave", "FREE");

        userService.deleteAccount();

        UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();
        assertThat(profile.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteAccount_noProfile_shouldThrowNotFound() {
        assertThatThrownBy(() -> userService.deleteAccount())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getGoals / updateGoals ────────────────────────────────────────────────

    @Test
    void updateGoals_newUser_shouldCreateGoals() {
        UserGoalsDto req = UserGoalsDto.builder()
                .dailySteps(10000)
                .sleepMinutes(480)
                .waterMl(2500)
                .targetWeightKg(new BigDecimal("70.0"))
                .build();

        UserGoalsDto result = userService.updateGoals(req);

        assertThat(result.getDailySteps()).isEqualTo(10000);
        assertThat(result.getSleepMinutes()).isEqualTo(480);
        assertThat(result.getWaterMl()).isEqualTo(2500);
    }

    @Test
    void updateGoals_existingGoals_shouldUpdate() {
        userGoalRepository.save(UserGoal.builder()
                .userId(userId)
                .dailySteps(5000)
                .sleepMinutes(420)
                .build());

        UserGoalsDto req = UserGoalsDto.builder()
                .dailySteps(12000)
                .build();

        userService.updateGoals(req);

        UserGoal saved = userGoalRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getDailySteps()).isEqualTo(12000);
        // sleepMinutes not in request — should remain 420
        assertThat(saved.getSleepMinutes()).isEqualTo(420);
    }

    @Test
    void getGoals_noGoals_shouldThrowNotFound() {
        assertThatThrownBy(() -> userService.getGoals())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGoals_existingGoals_shouldReturn() {
        userGoalRepository.save(UserGoal.builder()
                .userId(userId)
                .dailySteps(8000)
                .waterMl(2000)
                .build());

        UserGoalsDto goals = userService.getGoals();

        assertThat(goals.getDailySteps()).isEqualTo(8000);
        assertThat(goals.getWaterMl()).isEqualTo(2000);
    }

    // ── GDPR export ───────────────────────────────────────────────────────────

    @Test
    void exportData_withProfile_shouldContainUserData() {
        saveProfile(userId, "Eve", "PRO");
        userGoalRepository.save(UserGoal.builder()
                .userId(userId)
                .dailySteps(9000)
                .build());

        GdprExportDto export = userService.exportData();

        assertThat(export.getUserId()).isEqualTo(userId);
        assertThat(export.getEmail()).isEqualTo("test@health.com");
        assertThat(export.getExportedAt()).isNotNull();
        assertThat(export.getDataJson()).contains("Eve");
        assertThat(export.getDataJson()).contains("9000");
    }

    @Test
    void exportData_noProfile_shouldStillReturnExport() {
        GdprExportDto export = userService.exportData();

        assertThat(export.getUserId()).isEqualTo(userId);
        assertThat(export.getDataJson()).isNotBlank();
    }

    // ── subscription ──────────────────────────────────────────────────────────

    @Test
    void getSubscription_shouldReturnPlan() {
        saveProfile(userId, "Frank", "PREMIUM");

        SubscriptionDto sub = userService.getSubscription();

        assertThat(sub.getPlan()).isEqualTo("PREMIUM");
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateSubscription_shouldChangePlan() {
        saveProfile(userId, "Grace", "FREE");

        SubscriptionDto result = userService.updateSubscription("PRO");

        assertThat(result.getPlan()).isEqualTo("PRO");
        UserProfile saved = userProfileRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getSubscriptionPlan()).isEqualTo("PRO");
    }

    // ── user isolation ────────────────────────────────────────────────────────

    @Test
    void getProfile_differentUsers_shouldBeIsolated() {
        saveProfile(userId, "User1", "FREE");

        UUID user2 = UUID.randomUUID();
        setAuth(user2, "user2@health.com");
        saveProfile(user2, "User2", "PRO");

        // Switch back to user1
        setAuth(userId, "test@health.com");
        UserProfileResponse resp = userService.getProfile();
        assertThat(resp.getDisplayName()).isEqualTo("User1");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid, String email) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, email, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void saveProfile(UUID uid, String displayName, String plan) {
        userProfileRepository.save(UserProfile.builder()
                .userId(uid)
                .displayName(displayName)
                .subscriptionPlan(plan)
                .timezone("UTC")
                .build());
    }
}
