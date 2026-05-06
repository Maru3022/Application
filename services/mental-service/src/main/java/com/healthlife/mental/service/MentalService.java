package com.healthlife.mental.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlife.common.dto.mental.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.mental.entity.*;
import com.healthlife.mental.repository.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentalService {

    private final MoodEntryRepository moodEntryRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final StressEntryRepository stressEntryRepository;
    private final MeditationRepository meditationRepository;
    private final BreathingSessionRepository breathingSessionRepository;
    // FIX: use Jackson for serialising list fields instead of CSV join/split.
    // CSV is unsafe when values contain commas (data corruption).
    private final ObjectMapper objectMapper;

    @Transactional
    public MoodResponse createMood(MoodRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        MoodEntry entry = MoodEntry.builder()
                .userId(userId)
                .moodScore(request.getMoodScore())
                .emotions(toJson(request.getEmotions()))
                .note(request.getNote())
                .recordedAt(request.getRecordedAt())
                .build();
        entry = moodEntryRepository.save(entry);
        return toMoodResponse(entry);
    }

    public List<MoodResponse> getMoodHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return moodEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(this::toMoodResponse)
                .toList();
    }

    @Transactional
    public JournalResponse createJournal(JournalRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        JournalEntry entry = JournalEntry.builder()
                .userId(userId)
                .content(request.getContent())
                .tags(toJson(request.getTags()))
                .recordedAt(request.getRecordedAt())
                .build();
        entry = journalEntryRepository.save(entry);
        return toJournalResponse(entry);
    }

    public List<JournalResponse> getJournals() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return journalEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(this::toJournalResponse)
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
        // FIX: load only 1 meditation instead of all to prevent OOM
        List<Meditation> all = meditationRepository
                .findAll(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent();
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

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Serialises a list to a JSON array string for storage in a VARCHAR column.
     * Using JSON instead of CSV prevents data corruption when values contain commas.
     */
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise list to JSON, falling back to null: {}", e.getMessage());
            return null;
        }
    }

    /** Deserialises a JSON array string back to a list. Returns empty list on null/error. */
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            // Backward-compat: if stored value is old CSV format, split on comma
            log.debug("JSON parse failed for list field, attempting CSV fallback: {}", e.getMessage());
            return List.of(json.split(","));
        }
    }

    private MoodResponse toMoodResponse(MoodEntry e) {
        return MoodResponse.builder()
                .id(e.getId())
                .moodScore(e.getMoodScore())
                .emotions(fromJson(e.getEmotions()))
                .note(e.getNote())
                .recordedAt(e.getRecordedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private JournalResponse toJournalResponse(JournalEntry e) {
        return JournalResponse.builder()
                .id(e.getId())
                .content(e.getContent())
                .tags(fromJson(e.getTags()))
                .recordedAt(e.getRecordedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
