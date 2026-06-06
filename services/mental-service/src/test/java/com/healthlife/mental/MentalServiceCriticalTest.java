package com.healthlife.mental;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.mental.*;
import com.healthlife.mental.entity.Meditation;
import com.healthlife.mental.repository.*;
import com.healthlife.mental.service.MentalService;
import java.time.OffsetDateTime;
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

@SpringBootTest(classes = MentalServiceApplication.class)
@ActiveProfiles("test")
class MentalServiceCriticalTest {

    @Autowired
    private MentalService mentalService;

    @Autowired
    private MoodEntryRepository moodEntryRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private StressEntryRepository stressEntryRepository;

    @Autowired
    private MeditationRepository meditationRepository;

    @Autowired
    private BreathingSessionRepository breathingSessionRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        moodEntryRepository.deleteAll();
        journalEntryRepository.deleteAll();
        stressEntryRepository.deleteAll();
        breathingSessionRepository.deleteAll();
        meditationRepository.deleteAll();
    }

    @Test
    void createMood_shouldPersist() {
        MoodRequest req = MoodRequest.builder()
                .moodScore(8)
                .emotions(List.of("happy", "energetic"))
                .note("Great day!")
                .recordedAt(OffsetDateTime.now())
                .build();
        MoodResponse resp = mentalService.createMood(req);
        assertThat(resp.getMoodScore()).isEqualTo(8);
        assertThat(resp.getEmotions()).containsExactly("happy", "energetic");
    }

    @Test
    void createMood_nullEmotions_shouldWork() {
        MoodRequest req = MoodRequest.builder()
                .moodScore(5)
                .recordedAt(OffsetDateTime.now())
                .build();
        MoodResponse resp = mentalService.createMood(req);
        assertThat(resp.getMoodScore()).isEqualTo(5);
        assertThat(resp.getEmotions()).isNull();
    }

    @Test
    void createJournal_shouldPersist() {
        JournalRequest req = JournalRequest.builder()
                .content("Today I reflected on my goals")
                .tags(List.of("reflection", "goals"))
                .recordedAt(OffsetDateTime.now())
                .build();
        JournalResponse resp = mentalService.createJournal(req);
        assertThat(resp.getContent()).isEqualTo("Today I reflected on my goals");
        assertThat(resp.getTags()).containsExactly("reflection", "goals");
    }

    @Test
    void createStress_shouldPersist() {
        StressRequest req = StressRequest.builder()
                .level(7)
                .notes("Deadline approaching")
                .recordedAt(OffsetDateTime.now())
                .build();
        StressResponse resp = mentalService.createStress(req);
        assertThat(resp.getLevel()).isEqualTo(7);
    }

    @Test
    void getMeditations_withCategory_shouldFilter() {
        meditationRepository.save(Meditation.builder()
                .title("Calm")
                .category("sleep")
                .durationMin(10)
                .build());
        meditationRepository.save(Meditation.builder()
                .title("Focus")
                .category("focus")
                .durationMin(15)
                .build());

        List<MeditationDto> sleep = mentalService.getMeditations("sleep");
        assertThat(sleep).hasSize(1);
        assertThat(sleep.get(0).getTitle()).isEqualTo("Calm");
    }

    @Test
    void getMeditations_noCategory_shouldReturnAll() {
        meditationRepository.save(
                Meditation.builder().title("Calm").category("sleep").build());
        meditationRepository.save(
                Meditation.builder().title("Focus").category("focus").build());

        List<MeditationDto> all = mentalService.getMeditations(null);
        assertThat(all).hasSize(2);
    }

    @Test
    void createBreathingSession_shouldPersist() {
        BreathingSessionRequest req = BreathingSessionRequest.builder()
                .technique("4-7-8")
                .durationMin(5)
                .build();
        mentalService.createBreathingSession(req);
        assertThat(breathingSessionRepository.count()).isEqualTo(1);
    }

    @Test
    void moodHistory_shouldReturnOrdered() {
        mentalService.createMood(MoodRequest.builder()
                .moodScore(5)
                .recordedAt(OffsetDateTime.now().minusHours(2))
                .build());
        mentalService.createMood(MoodRequest.builder()
                .moodScore(8)
                .recordedAt(OffsetDateTime.now())
                .build());

        List<MoodResponse> history = mentalService.getMoodHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getMoodScore()).isEqualTo(8);
    }

    @Test
    void getJournals_shouldReturnList() {
        mentalService.createJournal(JournalRequest.builder()
                .content("Test journal")
                .recordedAt(OffsetDateTime.now())
                .build());

        List<JournalResponse> journals = mentalService.getJournals();
        assertThat(journals).hasSize(1);
        assertThat(journals.get(0).getContent()).isEqualTo("Test journal");
    }

    @Test
    void getStressStats_shouldReturnList() {
        mentalService.createStress(StressRequest.builder()
                .level(6)
                .recordedAt(OffsetDateTime.now())
                .build());

        List<StressResponse> stats = mentalService.getStressStats();
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getLevel()).isEqualTo(6);
    }

    @Test
    void getRecommendedMeditation_shouldReturnMeditation() {
        meditationRepository.save(
                Meditation.builder().title("Test Meditation").category("stress").build());

        MeditationDto recommended = mentalService.getRecommendedMeditation();
        assertThat(recommended.getTitle()).isEqualTo("Test Meditation");
    }
}
