package com.healthlife.healthdata.service;

import com.healthlife.common.dto.health.*;
import com.healthlife.common.exception.ResourceNotFoundException;
import com.healthlife.common.security.SecurityUtils;
import com.healthlife.healthdata.entity.*;
import com.healthlife.healthdata.repository.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthDataService {

    private final SleepEntryRepository sleepEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final ActivityEntryRepository activityEntryRepository;
    private final SymptomEntryRepository symptomEntryRepository;
    private final CycleEntryRepository cycleEntryRepository;

    @Transactional
    public SleepResponse createSleep(SleepRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SleepEntry entry = SleepEntry.builder()
                .userId(userId)
                .sleepStart(request.getSleepStart())
                .sleepEnd(request.getSleepEnd())
                .quality(request.getQuality())
                .notes(request.getNotes())
                .source(request.getSource() != null ? request.getSource() : "manual")
                .build();
        entry = sleepEntryRepository.save(entry);
        return mapSleepResponse(entry);
    }

    public List<SleepResponse> getSleepEntries(OffsetDateTime from, OffsetDateTime to) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<SleepEntry> entries;
        if (from != null && to != null) {
            entries = sleepEntryRepository.findByUserIdAndSleepStartBetweenOrderBySleepStartDesc(userId, from, to);
        } else {
            // FIX: limit to 100 most recent entries when no date range to prevent OOM
            entries = sleepEntryRepository
                    .findByUserIdOrderBySleepStartDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                    .getContent();
        }
        return entries.stream().map(this::mapSleepResponse).toList();
    }

    @Transactional
    public void deleteSleep(UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SleepEntry entry = sleepEntryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SleepEntry", "id", id));
        if (!entry.getUserId().equals(userId)) {
            throw new com.healthlife.common.exception.ForbiddenException("Not your entry");
        }
        sleepEntryRepository.delete(entry);
    }

    @Transactional
    public WeightResponse createWeight(WeightRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WeightEntry entry = WeightEntry.builder()
                .userId(userId)
                .weightKg(request.getWeightKg())
                .bodyFatPct(request.getBodyFatPct())
                .recordedAt(request.getRecordedAt())
                .build();
        entry = weightEntryRepository.save(entry);
        return WeightResponse.builder()
                .id(entry.getId())
                .weightKg(entry.getWeightKg())
                .bodyFatPct(entry.getBodyFatPct())
                .recordedAt(entry.getRecordedAt())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    public List<WeightResponse> getWeightHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // FIX: limit to 100 most recent entries to prevent OOM with large datasets
        return weightEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(e -> WeightResponse.builder()
                        .id(e.getId())
                        .weightKg(e.getWeightKg())
                        .bodyFatPct(e.getBodyFatPct())
                        .recordedAt(e.getRecordedAt())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public WaterResponse addWater(WaterRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WaterEntry entry = WaterEntry.builder()
                .userId(userId)
                .amountMl(request.getAmountMl())
                .recordedAt(request.getRecordedAt())
                .build();
        entry = waterEntryRepository.save(entry);
        return WaterResponse.builder()
                .id(entry.getId())
                .amountMl(entry.getAmountMl())
                .recordedAt(entry.getRecordedAt())
                .build();
    }

    public Integer getWaterToday() {
        UUID userId = SecurityUtils.getCurrentUserId();
        OffsetDateTime start =
                LocalDate.now().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime end = start.plusDays(1);
        return waterEntryRepository.findByUserIdAndRecordedAtBetween(userId, start, end).stream()
                .mapToInt(WaterEntry::getAmountMl)
                .sum();
    }

    @Transactional
    public SymptomResponse createSymptom(SymptomRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SymptomEntry entry = SymptomEntry.builder()
                .userId(userId)
                .symptom(request.getSymptom())
                .intensity(request.getIntensity())
                .recordedAt(request.getRecordedAt())
                .notes(request.getNotes())
                .build();
        entry = symptomEntryRepository.save(entry);
        return SymptomResponse.builder()
                .id(entry.getId())
                .symptom(entry.getSymptom())
                .intensity(entry.getIntensity())
                .recordedAt(entry.getRecordedAt())
                .notes(entry.getNotes())
                .build();
    }

    @Transactional
    public CycleResponse createCycle(CycleRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        CycleEntry entry = CycleEntry.builder()
                .userId(userId)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .cycleLength(request.getCycleLength())
                .flowIntensity(request.getFlowIntensity())
                .notes(request.getNotes())
                .build();
        entry = cycleEntryRepository.save(entry);
        return CycleResponse.builder()
                .id(entry.getId())
                .periodStart(entry.getPeriodStart())
                .periodEnd(entry.getPeriodEnd())
                .cycleLength(entry.getCycleLength())
                .flowIntensity(entry.getFlowIntensity())
                .notes(entry.getNotes())
                .build();
    }

    public CycleResponse getCyclePrediction() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<CycleEntry> entries = cycleEntryRepository
                .findByUserIdOrderByPeriodStartDesc(userId, org.springframework.data.domain.PageRequest.of(0, 12))
                .getContent();
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("Cycle data", "userId", userId);
        }
        CycleEntry last = entries.get(0);
        int avgCycle = (int) entries.stream()
                .filter(e -> e.getCycleLength() != null)
                .mapToInt(CycleEntry::getCycleLength)
                .average()
                .orElse(28);
        LocalDate nextStart = last.getPeriodStart().plusDays(avgCycle);
        return CycleResponse.builder()
                .periodStart(nextStart)
                .cycleLength(avgCycle)
                .build();
    }

    public ActivityEntryDto getActivityToday() {
        UUID userId = SecurityUtils.getCurrentUserId();
        ActivityEntry entry = activityEntryRepository
                .findByUserIdAndDate(userId, LocalDate.now())
                .orElse(ActivityEntry.builder()
                        .userId(userId)
                        .date(LocalDate.now())
                        .build());
        return mapActivityDto(entry);
    }

    public List<ActivityEntryDto> getActivityHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        // Limit to 100 most recent activity entries to prevent OOM
        return activityEntryRepository
                .findByUserIdOrderByDateDesc(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(this::mapActivityDto)
                .toList();
    }

    /**
     * Returns aggregated sleep statistics for the most recent 30 entries.
     * Entries without a computed duration are excluded from averages.
     */
    public SleepStatsDto getSleepStats() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<SleepEntry> entries = sleepEntryRepository
                .findByUserIdOrderBySleepStartDesc(userId, org.springframework.data.domain.PageRequest.of(0, 30))
                .getContent();

        if (entries.isEmpty()) {
            return SleepStatsDto.builder()
                    .entryCount(0)
                    .avgDurationMin(0)
                    .minDurationMin(0)
                    .maxDurationMin(0)
                    .goalAchievementPct(0)
                    .build();
        }

        List<Integer> durations = entries.stream()
                .map(SleepEntry::getDurationMin)
                .filter(d -> d != null && d > 0)
                .toList();

        double avg = durations.stream().mapToInt(Integer::intValue).average().orElse(0);
        int min = durations.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = durations.stream().mapToInt(Integer::intValue).max().orElse(0);

        long goalNights = durations.stream().filter(d -> d >= 420).count(); // 7 hours
        double goalPct = durations.isEmpty() ? 0 : (goalNights * 100.0) / durations.size();

        OptionalDouble avgQuality = entries.stream()
                .filter(e -> e.getQuality() != null)
                .mapToInt(SleepEntry::getQuality)
                .average();

        return SleepStatsDto.builder()
                .entryCount(entries.size())
                .avgDurationMin(Math.round(avg * 10.0) / 10.0)
                .avgQuality(avgQuality.isPresent() ? Math.round(avgQuality.getAsDouble() * 10.0) / 10.0 : null)
                .minDurationMin(min)
                .maxDurationMin(max)
                .goalAchievementPct(Math.round(goalPct * 10.0) / 10.0)
                .build();
    }

    public DashboardDto getDashboard() {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Water today
        int waterToday = getWaterToday();

        // Steps today
        Integer stepsToday = activityEntryRepository
                .findByUserIdAndDate(userId, LocalDate.now())
                .map(ActivityEntry::getSteps)
                .orElse(null);

        // Latest sleep
        List<SleepEntry> recentSleep = sleepEntryRepository
                .findByUserIdOrderBySleepStartDesc(userId, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent();
        Integer lastSleepDuration = null;
        Integer lastSleepQuality = null;
        if (!recentSleep.isEmpty()) {
            lastSleepDuration = recentSleep.get(0).getDurationMin();
            lastSleepQuality = recentSleep.get(0).getQuality();
        }

        // Latest weight
        Double latestWeight = weightEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst()
                .map(e -> e.getWeightKg() != null ? e.getWeightKg().doubleValue() : null)
                .orElse(null);

        return DashboardDto.builder()
                .waterTodayMl(waterToday)
                .stepsTodayCount(stepsToday)
                .lastSleepDurationMin(lastSleepDuration)
                .lastSleepQuality(lastSleepQuality)
                .latestWeightKg(latestWeight)
                .build();
    }

    @Transactional
    public ActivityEntryDto syncActivity(ActivityEntryDto dto) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ActivityEntry entry = activityEntryRepository
                .findByUserIdAndDate(userId, dto.getDate())
                .orElse(ActivityEntry.builder()
                        .userId(userId)
                        .date(dto.getDate())
                        .build());
        if (dto.getSteps() != null) entry.setSteps(dto.getSteps());
        if (dto.getCaloriesBurned() != null) entry.setCaloriesBurned(dto.getCaloriesBurned());
        if (dto.getActiveMinutes() != null) entry.setActiveMinutes(dto.getActiveMinutes());
        if (dto.getDistanceM() != null) entry.setDistanceM(dto.getDistanceM());
        if (dto.getSource() != null) entry.setSource(dto.getSource());
        entry = activityEntryRepository.save(entry);
        return mapActivityDto(entry);
    }

    private SleepResponse mapSleepResponse(SleepEntry e) {
        return SleepResponse.builder()
                .id(e.getId())
                .sleepStart(e.getSleepStart())
                .sleepEnd(e.getSleepEnd())
                .durationMin(e.getDurationMin())
                .quality(e.getQuality())
                .notes(e.getNotes())
                .source(e.getSource())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private ActivityEntryDto mapActivityDto(ActivityEntry e) {
        return ActivityEntryDto.builder()
                .id(e.getId())
                .date(e.getDate())
                .steps(e.getSteps())
                .caloriesBurned(e.getCaloriesBurned())
                .activeMinutes(e.getActiveMinutes())
                .distanceM(e.getDistanceM())
                .source(e.getSource())
                .build();
    }
}
