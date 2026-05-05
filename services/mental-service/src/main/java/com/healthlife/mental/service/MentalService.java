package com.healthlife.mental.service;

import com.healthlife.common.dto.mental.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.mental.entity.*;
import com.healthlife.mental.repository.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentalService {

    private final MoodEntryRepository moodEntryRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final StressEntryRepository stressEntryRepository;
    private final MeditationRepository meditationRepository;
    private final BreathingSessionRepository breathingSessionRepository;

    @Transactional
    public MoodResponse createMood(MoodRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        MoodEntry entry = MoodEntry.builder()
                .userId(userId)
                .moodScore(request.getMoodScore())
                .emotions(request.getEmotions() != null ? String.join(",", request.getEmotions()) : null)
                .note(request.getNote())
                .recordedAt(request.getRecordedAt())
                .build();
        entry = moodEntryRepository.save(entry);
        return MoodResponse.builder()
                .id(entry.getId())
                .moodScore(entry.getMoodScore())
                .emotions(
                        entry.getEmotions() != null
                                ? Arrays.asList(entry.getEmotions().split(","))
                                : null)
                .note(entry.getNote())
                .recordedAt(entry.getRecordedAt())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    public List<MoodResponse> getMoodHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return moodEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(e -> MoodResponse.builder()
                        .id(e.getId())
                        .moodScore(e.getMoodScore())
                        .emotions(
                                e.getEmotions() != null
                                        ? Arrays.asList(e.getEmotions().split(","))
                                        : null)
                        .note(e.getNote())
                        .recordedAt(e.getRecordedAt())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public JournalResponse createJournal(JournalRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        JournalEntry entry = JournalEntry.builder()
                .userId(userId)
                .content(request.getContent())
                .tags(request.getTags() != null ? String.join(",", request.getTags()) : null)
                .recordedAt(request.getRecordedAt())
                .build();
        entry = journalEntryRepository.save(entry);
        return JournalResponse.builder()
                .id(entry.getId())
                .content(entry.getContent())
                .tags(entry.getTags() != null ? Arrays.asList(entry.getTags().split(",")) : null)
                .recordedAt(entry.getRecordedAt())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    public List<JournalResponse> getJournals() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return journalEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(e -> JournalResponse.builder()
                        .id(e.getId())
                        .content(e.getContent())
                        .tags(e.getTags() != null ? Arrays.asList(e.getTags().split(",")) : null)
                        .recordedAt(e.getRecordedAt())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public StressResponse createStress(StressRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        StressEntry entry = StressEntry.builder()
                .userId(userId)
                .level(request.getLevel())
                .recordedAt(request.getRecordedAt())
                .notes(request.getNotes())
                .build();
        entry = stressEntryRepository.save(entry);
        return StressResponse.builder()
                .id(entry.getId())
                .level(entry.getLevel())
                .recordedAt(entry.getRecordedAt())
                .notes(entry.getNotes())
                .build();
    }

    public List<StressResponse> getStressStats() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return stressEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(e -> StressResponse.builder()
                        .id(e.getId())
                        .level(e.getLevel())
                        .recordedAt(e.getRecordedAt())
                        .notes(e.getNotes())
                        .build())
                .toList();
    }

    public List<MeditationDto> getMeditations(String category) {
        List<Meditation> meditations;
        if (category != null) {
            meditations = meditationRepository.findByCategory(category);
        } else {
            meditations = meditationRepository.findAll();
        }
        return meditations.stream()
                .map(m -> MeditationDto.builder()
                        .id(m.getId())
                        .title(m.getTitle())
                        .description(m.getDescription())
                        .durationMin(m.getDurationMin())
                        .category(m.getCategory())
                        .audioUrl(m.getAudioUrl())
                        .build())
                .toList();
    }

    @Transactional
    public void completeMeditation(UUID meditationId) {
        // Track meditation completion
    }

    public MeditationDto getRecommendedMeditation() {
        // AI-based recommendation placeholder
        List<Meditation> all = meditationRepository.findAll();
        if (all.isEmpty()) throw new ResourceNotFoundException("Meditation", "any", "");
        Meditation m = all.get(0);
        return MeditationDto.builder()
                .id(m.getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .durationMin(m.getDurationMin())
                .category(m.getCategory())
                .audioUrl(m.getAudioUrl())
                .build();
    }

    @Transactional
    public void createBreathingSession(BreathingSessionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        BreathingSession session = BreathingSession.builder()
                .userId(userId)
                .technique(request.getTechnique())
                .durationMin(request.getDurationMin())
                .recordedAt(request.getRecordedAt() != null ? request.getRecordedAt() : java.time.OffsetDateTime.now())
                .build();
        breathingSessionRepository.save(session);
    }
}
