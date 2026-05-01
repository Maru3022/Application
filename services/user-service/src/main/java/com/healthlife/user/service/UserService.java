package com.healthlife.user.service;

import com.healthlife.common.dto.user.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.user.entity.UserGoal;
import com.healthlife.user.entity.UserProfile;
import com.healthlife.user.repository.UserGoalRepository;
import com.healthlife.user.repository.UserProfileRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserGoalRepository userGoalRepository;
    private final PasswordEncoder passwordEncoder;

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
                .email(null)
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
