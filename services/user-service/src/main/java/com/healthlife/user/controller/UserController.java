package com.healthlife.user.controller;

import com.healthlife.common.dto.user.*;
import com.healthlife.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount() {
        userService.deleteAccount();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/data-export")
    public ResponseEntity<GdprExportDto> exportData() {
        return ResponseEntity.ok(userService.exportData());
    }

    @GetMapping("/me/goals")
    public ResponseEntity<UserGoalsDto> getGoals() {
        return ResponseEntity.ok(userService.getGoals());
    }

    @PutMapping("/me/goals")
    public ResponseEntity<UserGoalsDto> updateGoals(@Valid @RequestBody UserGoalsDto request) {
        return ResponseEntity.ok(userService.updateGoals(request));
    }

    @GetMapping("/me/subscription")
    public ResponseEntity<SubscriptionDto> getSubscription() {
        return ResponseEntity.ok(userService.getSubscription());
    }

    @PostMapping("/me/subscription")
    public ResponseEntity<SubscriptionDto> updateSubscription(@RequestParam String plan) {
        return ResponseEntity.ok(userService.updateSubscription(plan));
    }
}
