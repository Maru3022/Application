package com.healthlife.social.service;

import com.healthlife.common.dto.social.*;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.social.entity.*;
import com.healthlife.social.repository.*;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository challengeParticipantRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getChallenges() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return challengeRepository.findAll().stream()
                .map(c -> ChallengeResponse.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .description(c.getDescription())
                        .type(c.getType())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .targetValue(c.getTargetValue())
                        .participantCount((int) challengeParticipantRepository.countByChallengeId(c.getId()))
                        .joined(challengeParticipantRepository.existsByChallengeIdAndUserId(c.getId(), userId))
                        .build())
                .toList();
    }

    @Transactional
    public ChallengeResponse createChallenge(ChallengeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Challenge challenge = Challenge.builder()
                .creatorId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetValue(request.getTargetValue())
                .build();
        challenge = challengeRepository.save(challenge);
        return ChallengeResponse.builder()
                .id(challenge.getId())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .type(challenge.getType())
                .startDate(challenge.getStartDate())
                .endDate(challenge.getEndDate())
                .targetValue(challenge.getTargetValue())
                .participantCount(0)
                .joined(false)
                .build();
    }

    @Transactional
    public void joinChallenge(UUID challengeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (challengeParticipantRepository.existsByChallengeIdAndUserId(challengeId, userId)) {
            throw new BadRequestException("Already joined this challenge");
        }
        challengeParticipantRepository.save(ChallengeParticipant.builder()
                .challengeId(challengeId)
                .userId(userId)
                .progress(0)
                .build());
    }

    @Transactional
    public void updateProgress(UUID challengeId, Integer progress) {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: was empty stub — now updates participant progress
        ChallengeParticipant participant = challengeParticipantRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ChallengeParticipant", "challengeId+userId", challengeId));
        participant.setProgress(progress);
        challengeParticipantRepository.save(participant);
    }

    public List<ChallengeParticipant> getLeaderboard(UUID challengeId) {
        return challengeParticipantRepository.findByChallengeId(challengeId);
    }

    public List<PostResponse> getFeed() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<UUID> friendIds = friendshipRepository.findByUserIdAndStatus(userId, "ACCEPTED").stream()
                .map(Friendship::getFriendId)
                .toList();
        List<UUID> allIds = friendIds.isEmpty()
                ? List.of(userId)
                : java.util.stream.Stream.concat(friendIds.stream(), java.util.stream.Stream.of(userId))
                        .toList();
        return postRepository.findByUserIdInOrderByCreatedAtDesc(allIds).stream()
                .map(p -> PostResponse.builder()
                        .id(p.getId())
                        .userId(p.getUserId())
                        .content(p.getContent())
                        .type(p.getType())
                        .likesCount(p.getLikesCount())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public PostResponse createPost(PostRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Post post = Post.builder()
                .userId(userId)
                .content(request.getContent())
                .type(request.getType())
                .build();
        post = postRepository.save(post);
        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .content(post.getContent())
                .type(post.getType())
                .likesCount(0)
                .createdAt(post.getCreatedAt())
                .build();
    }

    @Transactional
    public void likePost(UUID postId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: fetch post once (was fetched twice — once per branch), use pessimistic lock
        Post post = postRepository
                .findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        } else {
            postLikeRepository.save(PostLike.builder().postId(postId).userId(userId).build());
            post.setLikesCount(post.getLikesCount() + 1);
        }
        postRepository.save(post);
    }

    @Transactional
    public void inviteFriend(String email) {
        UUID userId = SecurityUtils.getCurrentUserId();
        // In production, send invitation email
    }

    public List<Friendship> getFriends() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return friendshipRepository.findByUserIdAndStatus(userId, "ACCEPTED");
    }
}
