package com.healthlife.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.user.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.user.entity.UserGoal;
import com.healthlife.user.entity.UserProfile;
import com.healthlife.user.repository.UserGoalRepository;
import com.healthlife.user.repository.UserProfileRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserGoalRepository userGoalRepository;
    private final ObjectMapper objectMapper;

    public UserProfileResponse getProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));
        return mapToResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));

        if (request.getDisplayName() != null) profile.setDisplayName(request.getDisplayName());
        if (request.getTimezone() != null) profile.setTimezone(request.getTimezone());
        if (request.getDateOfBirth() != null) profile.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getHeightCm() != null) profile.setHeightCm(request.getHeightCm());

        return mapToResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public void deleteAccount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));
        profile.setDeletedAt(OffsetDateTime.now());
        userProfileRepository.save(profile);
    }

    /**
     * GDPR Article 20 — Right to data portability.
     *
     * <p>Assembles all personal data stored in user-service for the requesting user and returns it
     * as a structured JSON export. Other services (health-data, mental, nutrition, etc.) should
     * expose their own export endpoints; an orchestration layer can aggregate them.
     */
    public GdprExportDto exportData() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String email = SecurityUtils.getCurrentUserEmail();

        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        UserGoal goal = userGoalRepository.findByUserId(userId).orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId.toString());
        data.put("email", email);
        data.put("exportedAt", OffsetDateTime.now().toString());

        if (profile != null) {
            Map<String, Object> profileMap = new LinkedHashMap<>();
            profileMap.put("displayName", profile.getDisplayName());
            profileMap.put("timezone", profile.getTimezone());
            profileMap.put(
                    "dateOfBirth",
                    profile.getDateOfBirth() != null ? profile.getDateOfBirth().toString() : null);
            profileMap.put("gender", profile.getGender());
            profileMap.put("heightCm", profile.getHeightCm());
            profileMap.put("avatarUrl", profile.getAvatarUrl());
            profileMap.put("subscriptionPlan", profile.getSubscriptionPlan());
            profileMap.put(
                    "createdAt",
                    profile.getCreatedAt() != null ? profile.getCreatedAt().toString() : null);
            data.put("profile", profileMap);
        }

        if (goal != null) {
            Map<String, Object> goalMap = new LinkedHashMap<>();
            goalMap.put("dailySteps", goal.getDailySteps());
            goalMap.put("sleepMinutes", goal.getSleepMinutes());
            goalMap.put("waterMl", goal.getWaterMl());
            goalMap.put("targetWeightKg", goal.getTargetWeightKg());
            data.put("goals", goalMap);
        }

        String dataJson;
        try {
            dataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise GDPR export for user={}: {}", userId, e.getMessage());
            dataJson = "{\"error\":\"Export serialisation failed\"}";
        }

        log.info("GDPR data export generated for user={}", userId);
        return GdprExportDto.builder()
                .userId(userId)
                .email(email)
                .exportedAt(OffsetDateTime.now())
                .dataJson(dataJson)
                .build();
    }

    public UserGoalsDto getGoals() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserGoal goal = userGoalRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Goals", "userId", userId));
        return UserGoalsDto.builder()
                .dailySteps(goal.getDailySteps())
                .sleepMinutes(goal.getSleepMinutes())
                .waterMl(goal.getWaterMl())
                .targetWeightKg(goal.getTargetWeightKg())
                .build();
    }

    @Transactional
    public UserGoalsDto updateGoals(UserGoalsDto request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserGoal goal = userGoalRepository
                .findByUserId(userId)
                .orElseGet(() -> UserGoal.builder().userId(userId).build());

        if (request.getDailySteps() != null) goal.setDailySteps(request.getDailySteps());
        if (request.getSleepMinutes() != null) goal.setSleepMinutes(request.getSleepMinutes());
        if (request.getWaterMl() != null) goal.setWaterMl(request.getWaterMl());
        if (request.getTargetWeightKg() != null) goal.setTargetWeightKg(request.getTargetWeightKg());

        userGoalRepository.save(goal);
        return request;
    }

    public SubscriptionDto getSubscription() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));
        return SubscriptionDto.builder()
                .plan(profile.getSubscriptionPlan())
                .status("ACTIVE")
                .build();
    }

    @Transactional
    public SubscriptionDto updateSubscription(String plan) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "userId", userId));
        profile.setSubscriptionPlan(plan);
        userProfileRepository.save(profile);
        return SubscriptionDto.builder().plan(plan).status("ACTIVE").build();
    }

    private UserProfileResponse mapToResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getUserId())
                .email(SecurityUtils.getCurrentUserEmail())
                .displayName(p.getDisplayName())
                .timezone(p.getTimezone())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .heightCm(p.getHeightCm())
                .avatarUrl(p.getAvatarUrl())
                .subscriptionPlan(p.getSubscriptionPlan())
                .build();
    }
}
