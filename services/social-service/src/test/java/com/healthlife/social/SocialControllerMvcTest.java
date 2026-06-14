package com.healthlife.social;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthlife.common.dto.social.*;
import com.healthlife.common.exception.BadRequestException;
import com.healthlife.common.security.JwtTokenProvider;
import com.healthlife.social.service.SocialService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for SocialController covering:
 * - GET /challenges → 200
 * - POST /challenges → 200
 * - POST /challenges/{id}/join → 200 / 400 already joined
 * - GET /feed → 200
 * - POST /posts → 200
 * - POST /posts/{id}/like → 200
 * - GET /friends → 200
 * - 401 without JWT
 */
@SpringBootTest(classes = SocialServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    SocialService socialService;

    private String jwt() {
        return jwtTokenProvider.generateAccessToken(UUID.randomUUID(), "u@t.com", "USER");
    }

    // ── GET /api/v1/social/challenges ─────────────────────────────────────────

    @Test
    void getChallenges_shouldReturn200() throws Exception {
        when(socialService.getChallenges())
                .thenReturn(List.of(ChallengeResponse.builder()
                        .id(UUID.randomUUID())
                        .title("10K Steps")
                        .build()));

        mockMvc.perform(get("/api/v1/social/challenges").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("10K Steps"));
    }

    @Test
    void getChallenges_withoutJwt_shouldReturn4xx() throws Exception {
        mockMvc.perform(get("/api/v1/social/challenges")).andExpect(status().is4xxClientError());
    }

    // ── POST /api/v1/social/challenges ────────────────────────────────────────

    @Test
    void createChallenge_shouldReturn200() throws Exception {
        when(socialService.createChallenge(any()))
                .thenReturn(ChallengeResponse.builder()
                        .id(UUID.randomUUID())
                        .title("New Challenge")
                        .build());

        mockMvc.perform(
                        post("/api/v1/social/challenges")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"title":"New Challenge","type":"steps","startDate":"2025-01-01","endDate":"2025-01-31"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Challenge"));
    }

    // ── POST /api/v1/social/challenges/{id}/join ──────────────────────────────

    @Test
    void joinChallenge_shouldReturn200() throws Exception {
        doNothing().when(socialService).joinChallenge(any());

        mockMvc.perform(post("/api/v1/social/challenges/" + UUID.randomUUID() + "/join")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void joinChallenge_alreadyJoined_shouldReturn400() throws Exception {
        doThrow(new BadRequestException("Already joined")).when(socialService).joinChallenge(any());

        mockMvc.perform(post("/api/v1/social/challenges/" + UUID.randomUUID() + "/join")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProgress_shouldReturn200() throws Exception {
        doNothing().when(socialService).updateProgress(any(), any());

        mockMvc.perform(post("/api/v1/social/challenges/" + UUID.randomUUID() + "/progress")
                        .param("progress", "1000")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void inviteFriend_shouldReturn200() throws Exception {
        doNothing().when(socialService).inviteFriend(any());

        mockMvc.perform(post("/api/v1/social/friends/invite")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("test@example.com")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/social/feed ───────────────────────────────────────────────

    @Test
    void getFeed_shouldReturn200() throws Exception {
        when(socialService.getFeed())
                .thenReturn(List.of(PostResponse.builder()
                        .id(UUID.randomUUID())
                        .content("Hello!")
                        .likesCount(3)
                        .build()));

        mockMvc.perform(get("/api/v1/social/feed").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello!"));
    }

    @Test
    void getFeed_empty_shouldReturn200WithEmptyList() throws Exception {
        when(socialService.getFeed()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/social/feed").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── POST /api/v1/social/posts ─────────────────────────────────────────────

    @Test
    void createPost_shouldReturn200() throws Exception {
        when(socialService.createPost(any()))
                .thenReturn(PostResponse.builder()
                        .id(UUID.randomUUID())
                        .content("My achievement!")
                        .likesCount(0)
                        .build());

        mockMvc.perform(
                        post("/api/v1/social/posts")
                                .header("Authorization", "Bearer " + jwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                            {"content":"My achievement!","type":"achievement"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("My achievement!"));
    }

    // ── POST /api/v1/social/posts/{id}/like ───────────────────────────────────

    @Test
    void likePost_shouldReturn200() throws Exception {
        doNothing().when(socialService).likePost(any());

        mockMvc.perform(post("/api/v1/social/posts/" + UUID.randomUUID() + "/like")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/social/friends ────────────────────────────────────────────

    @Test
    void getFriends_shouldReturn200() throws Exception {
        when(socialService.getFriends())
                .thenReturn(List.of(FriendDto.builder()
                        .friendId(UUID.randomUUID())
                        .status("accepted")
                        .build()));

        mockMvc.perform(get("/api/v1/social/friends").header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("accepted"));
    }

    @Test
    void addFriend_shouldReturn200() throws Exception {
        doNothing().when(socialService).addFriend(any());

        mockMvc.perform(post("/api/v1/social/friends/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void removeFriend_shouldReturn200() throws Exception {
        doNothing().when(socialService).removeFriend(any());

        mockMvc.perform(delete("/api/v1/social/friends/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/social/challenges/{id}/leaderboard ────────────────────────

    @Test
    void getLeaderboard_shouldReturn200() throws Exception {
        when(socialService.getLeaderboard(any()))
                .thenReturn(List.of(LeaderboardEntryDto.builder()
                        .userId(UUID.randomUUID())
                        .progress(9000)
                        .build()));

        mockMvc.perform(get("/api/v1/social/challenges/" + UUID.randomUUID() + "/leaderboard")
                        .header("Authorization", "Bearer " + jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].progress").value(9000));
    }
}
