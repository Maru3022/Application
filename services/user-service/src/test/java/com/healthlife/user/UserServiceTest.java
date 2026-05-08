package com.healthlife.user;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.user.*;
import com.healthlife.user.entity.UserGoal;
import com.healthlife.user.entity.UserProfile;
import com.healthlife.user.repository.UserGoalRepository;
import com.healthlife.user.repository.UserProfileRepository;
import com.healthlife.user.service.UserService;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = UserServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileRepository profileRepository;

    @Autowired
    private UserGoalRepository goalRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        // Authenticate as this user (email stored as credentials for SecurityUtils.getCurrentUserEmail)
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@healthlife.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        profileRepository.deleteAll();
        goalRepository.deleteAll();

        // Seed a profile
        profileRepository.save(UserProfile.builder()
                .userId(userId)
                .displayName("Test User")
                .timezone("UTC")
                .subscriptionPlan("FREE")
                .build());
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Test
    void getProfile_shouldReturnProfile() {
        UserProfileResponse resp = userService.getProfile();
        assertThat(resp.getDisplayName()).isEqualTo("Test User");
        assertThat(resp.getTimezone()).isEqualTo("UTC");
        assertThat(resp.getEmail()).isEqualTo("test@healthlife.com");
    }

    @Test
    void updateProfile_shouldPersistChanges() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Updated Name");
        req.setTimezone("America/New_York");
        req.setGender("male");
        req.setHeightCm(new BigDecimal("180.5"));

        UserProfileResponse resp = userService.updateProfile(req);

        assertThat(resp.getDisplayName()).isEqualTo("Updated Name");
        assertThat(resp.getTimezone()).isEqualTo("America/New_York");
        assertThat(resp.getGender()).isEqualTo("male");
        assertThat(resp.getHeightCm()).isEqualByComparingTo("180.5");
    }

    @Test
    void updateProfile_nullFields_shouldNotOverwrite() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("New Name");
        // timezone is null — should not overwrite existing "UTC"

        UserProfileResponse resp = userService.updateProfile(req);

        assertThat(resp.getDisplayName()).isEqualTo("New Name");
        assertThat(resp.getTimezone()).isEqualTo("UTC"); // unchanged
    }

    @Test
    void deleteAccount_shouldSoftDelete() {
        userService.deleteAccount();

        UserProfile profile = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(profile.getDeletedAt()).isNotNull();
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Test
    void updateGoals_shouldCreateIfNotExists() {
        UserGoalsDto req = UserGoalsDto.builder()
                .dailySteps(8000)
                .sleepMinutes(420)
                .waterMl(2500)
                .build();

        UserGoalsDto result = userService.updateGoals(req);

        assertThat(result.getDailySteps()).isEqualTo(8000);
        assertThat(result.getSleepMinutes()).isEqualTo(420);
        assertThat(result.getWaterMl()).isEqualTo(2500);
    }

    @Test
    void updateGoals_shouldUpdateExisting() {
        goalRepository.save(UserGoal.builder()
                .userId(userId)
                .dailySteps(5000)
                .sleepMinutes(480)
                .waterMl(2000)
                .build());

        UserGoalsDto req = UserGoalsDto.builder().dailySteps(12000).build();
        userService.updateGoals(req);

        UserGoal saved = goalRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getDailySteps()).isEqualTo(12000);
        assertThat(saved.getSleepMinutes()).isEqualTo(480); // unchanged
    }

    @Test
    void getGoals_shouldReturnGoals() {
        goalRepository.save(UserGoal.builder()
                .userId(userId)
                .dailySteps(10000)
                .sleepMinutes(480)
                .waterMl(2000)
                .build());

        UserGoalsDto goals = userService.getGoals();
        assertThat(goals.getDailySteps()).isEqualTo(10000);
    }

    // ── Subscription ──────────────────────────────────────────────────────────

    @Test
    void getSubscription_shouldReturnFreeByDefault() {
        SubscriptionDto sub = userService.getSubscription();
        assertThat(sub.getPlan()).isEqualTo("FREE");
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateSubscription_shouldChangePlan() {
        SubscriptionDto sub = userService.updateSubscription("PRO");
        assertThat(sub.getPlan()).isEqualTo("PRO");
    }

    // ── GDPR Export ───────────────────────────────────────────────────────────

    @Test
    void exportData_shouldReturnJsonWithUserData() {
        GdprExportDto export = userService.exportData();

        assertThat(export.getUserId()).isEqualTo(userId);
        assertThat(export.getEmail()).isEqualTo("test@healthlife.com");
        assertThat(export.getDataJson()).contains("Test User");
        assertThat(export.getDataJson()).contains("UTC");
        assertThat(export.getExportedAt()).isNotNull();
    }
}
