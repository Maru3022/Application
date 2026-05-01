package com.healthlife.social;

import com.healthlife.common.dto.social.*;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.social.entity.*;
import com.healthlife.social.repository.*;
import com.healthlife.social.service.SocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = SocialServiceApplication.class)
@ActiveProfiles("test")
class SocialServiceCriticalTest {

    @Autowired private SocialService socialService;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private ChallengeParticipantRepository challengeParticipantRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private FriendshipRepository friendshipRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        challengeParticipantRepository.deleteAll();
        challengeRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        friendshipRepository.deleteAll();
    }

    @Test
    void createChallenge_shouldPersist() {
        ChallengeRequest req = ChallengeRequest.builder()
                .title("10K Steps").description("Walk 10K steps daily")
                .type("steps").startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                .targetValue(10000).build();
        ChallengeResponse resp = socialService.createChallenge(req);
        assertThat(resp.getTitle()).isEqualTo("10K Steps");
        assertThat(resp.isJoined()).isFalse();
    }

    @Test
    void joinChallenge_shouldAddParticipant() {
        Challenge challenge = challengeRepository.save(Challenge.builder()
                .creatorId(userId).title("Test").type("steps")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7)).build());

        socialService.joinChallenge(challenge.getId());
        assertThat(challengeParticipantRepository.existsByChallengeIdAndUserId(challenge.getId(), userId)).isTrue();
    }

    @Test
    void joinChallenge_alreadyJoined_shouldThrow() {
        Challenge challenge = challengeRepository.save(Challenge.builder()
                .creatorId(userId).title("Test").type("steps")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7)).build());

        socialService.joinChallenge(challenge.getId());
        assertThatThrownBy(() -> socialService.joinChallenge(challenge.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createPost_shouldPersist() {
        PostRequest req = PostRequest.builder().content("Hit my step goal today!").type("achievement").build();
        PostResponse resp = socialService.createPost(req);
        assertThat(resp.getContent()).isEqualTo("Hit my step goal today!");
        assertThat(resp.getLikesCount()).isEqualTo(0);
    }

    @Test
    void likePost_shouldToggleAndIncrement() {
        Post post = postRepository.save(Post.builder().userId(userId).content("Test post").likesCount(0).build());

        socialService.likePost(post.getId());
        Post updated = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updated.getLikesCount()).isEqualTo(1);

        socialService.likePost(post.getId());
        Post afterUnlike = postRepository.findById(post.getId()).orElseThrow();
        assertThat(afterUnlike.getLikesCount()).isEqualTo(0);
    }

    @Test
    void getFeed_withNoFriends_shouldReturnOwnPosts() {
        postRepository.save(Post.builder().userId(userId).content("My post").build());
        List<PostResponse> feed = socialService.getFeed();
        assertThat(feed).hasSize(1);
    }

    @Test
    void getChallenges_shouldShowJoinedStatus() {
        Challenge c = challengeRepository.save(Challenge.builder()
                .creatorId(UUID.randomUUID()).title("Public Challenge").type("water")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7)).build());

        List<ChallengeResponse> challenges = socialService.getChallenges();
        assertThat(challenges).hasSize(1);
        assertThat(challenges.get(0).isJoined()).isFalse();
    }
}
