package com.healthlife.social.service;

import com.healthlife.common.dto.social.*;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.social.entity.*;
import com.healthlife.social.repository.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class SocialService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository challengeParticipantRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final FriendshipRepository friendshipRepository;
    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;

    public SocialService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository challengeParticipantRepository,
            PostRepository postRepository,
            PostLikeRepository postLikeRepository,
            FriendshipRepository friendshipRepository,
            @Qualifier("socialRestTemplate") RestTemplate restTemplate,
            @Value("${internal.notification-service.url:http://notification-service:8088}")
                    String notificationServiceUrl) {
        this.challengeRepository = challengeRepository;
        this.challengeParticipantRepository = challengeParticipantRepository;
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.friendshipRepository = friendshipRepository;
        this.restTemplate = restTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getChallenges() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 50 most recent challenges to prevent OOM
        List<Challenge> challenges = challengeRepository
                .findAllByOrderByStartDateDesc(org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent();
        if (challenges.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = challenges.stream().map(Challenge::getId).toList();

        // FIX N+1: single bulk query instead of N count queries
        Map<UUID, Long> countMap = challengeParticipantRepository.countByChallengeIdIn(ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        // FIX N+1: single bulk query instead of N exists queries
        Set<UUID> joinedIds = challengeParticipantRepository.findJoinedChallengeIds(ids, userId);

        return challenges.stream()
                .map(c -> ChallengeResponse.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .description(c.getDescription())
                        .type(c.getType())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .targetValue(c.getTargetValue())
                        .participantCount(countMap.getOrDefault(c.getId(), 0L).intValue())
                        .joined(joinedIds.contains(c.getId()))
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
                .orElseThrow(() -> new ResourceNotFoundException("ChallengeParticipant", "id", challengeId));
        participant.setProgress(progress);
        challengeParticipantRepository.save(participant);
    }

    public List<LeaderboardEntryDto> getLeaderboard(UUID challengeId) {
        // FIX: limit to top 50 participants to prevent OOM
        // FIX: return DTO instead of JPA entity to avoid lazy-loading issues and
        //      accidental serialisation of internal fields.
        return challengeParticipantRepository
                .findByChallengeIdOrderByProgressDesc(
                        challengeId, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(p -> LeaderboardEntryDto.builder()
                        .userId(p.getUserId())
                        .progress(p.getProgress())
                        .build())
                .toList();
    }

    public List<PostResponse> getFeed() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<UUID> friendIds = friendshipRepository.findByUserIdAndStatus(userId, "ACCEPTED").stream()
                .map(Friendship::getFriendId)
                .toList();
        List<UUID> allIds = friendIds.isEmpty()
                ? List.of(userId)
                : Stream.concat(friendIds.stream(), Stream.of(userId)).toList();
        // FIX: limit to 50 most recent posts to prevent OOM
        return postRepository
                .findByUserIdInOrderByCreatedAtDesc(allIds, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent()
                .stream()
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
        Post post =
                postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        } else {
            try {
                postLikeRepository.save(
                        PostLike.builder().postId(postId).userId(userId).build());
                post.setLikesCount(post.getLikesCount() + 1);
            } catch (DataIntegrityViolationException e) {
                // Concurrent like already inserted — treat as idempotent success
                log.debug("Duplicate like ignored for post={} user={}", postId, userId);
            }
        }
        postRepository.save(post);
    }

    @Transactional
    public void inviteFriend(String email) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Friend invitation requested by user={} to email={}", userId, email);

        try {
            String url = notificationServiceUrl + "/api/v1/notifications/email?to={to}&subject={subject}";
            restTemplate.postForEntity(
                    url,
                    "You have been invited to join HealthLife! Your friend (user: " + userId
                            + ") wants to connect with you.",
                    Void.class,
                    email,
                    "HealthLife - Friend Invitation");
            log.info("Friend invitation email sent to {} via notification-service", email);
        } catch (Exception ex) {
            log.error("Failed to send friend invitation email to {}: {}", email, ex.getMessage());
        }
    }

    public List<FriendDto> getFriends() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: return DTO instead of JPA entity to avoid lazy-loading issues and
        //      accidental serialisation of internal fields.
        return friendshipRepository.findByUserIdAndStatus(userId, "ACCEPTED").stream()
                .map(f -> FriendDto.builder()
                        .friendId(f.getFriendId())
                        .status(f.getStatus())
                        .since(f.getCreatedAt())
                        .build())
                .toList();
    }
}
