package com.healthlife.social.controller;

import com.healthlife.common.dto.social.*;
import com.healthlife.social.service.SocialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @GetMapping("/challenges")
    public ResponseEntity<List<ChallengeResponse>> getChallenges() {
        return ResponseEntity.ok(socialService.getChallenges());
    }

    @PostMapping("/challenges")
    public ResponseEntity<ChallengeResponse> createChallenge(@Valid @RequestBody ChallengeRequest request) {
        return ResponseEntity.ok(socialService.createChallenge(request));
    }

    @PostMapping("/challenges/{id}/join")
    public ResponseEntity<Void> joinChallenge(@PathVariable UUID id) {
        socialService.joinChallenge(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/challenges/{id}/progress")
    public ResponseEntity<Void> updateProgress(@PathVariable UUID id, @RequestParam Integer progress) {
        socialService.updateProgress(id, progress);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/challenges/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard(@PathVariable UUID id) {
        return ResponseEntity.ok(socialService.getLeaderboard(id));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed() {
        return ResponseEntity.ok(socialService.getFeed());
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.ok(socialService.createPost(request));
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<Void> likePost(@PathVariable UUID id) {
        socialService.likePost(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/friends/invite")
    public ResponseEntity<Void> inviteFriend(@RequestBody String email) {
        socialService.inviteFriend(email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/friends")
    public ResponseEntity<List<FriendDto>> getFriends() {
        return ResponseEntity.ok(socialService.getFriends());
    }
}
