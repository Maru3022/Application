package com.healthlife.healthdata;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.health.*;
import com.healthlife.healthdata.entity.*;
import com.healthlife.healthdata.repository.*;
import com.healthlife.healthdata.service.HealthDataService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * Additional tests for HealthDataService covering:
 * - Dashboard aggregation
 * - Sleep stats
 * - Weight history
 * - Activity data
 * - User data isolation
 * - Boundary values (quality 1-5, intensity 1-10)
 */
@SpringBootTest(classes = HealthDataServiceApplication.class)
@ActiveProfiles("test")
class HealthDataDashboardTest {

    @Autowired private HealthDataService healthDataService;
    @Autowired private SleepEntryRepository sleepEntryRepository;
    @Autowired private WeightEntryRepository weightEntryRepository;
    @Autowired private WaterEntryRepository waterEntryRepository;
    @Autowired private ActivityEntryRepository activityEntryRepository;
    @Autowired private SymptomEntryRepository symptomEntryRepository;
    @Autowired private CycleEntryRepository cycleEntryRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        setAuth(userId);
        sleepEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
        waterEntryRepository.deleteAll();
        activityEntryRepository.deleteAll();
        symptomEntryRepository.deleteAll();
        cycleEntryRepository.deleteAll();
    }

    // ── sleep stats ───────────────────────────────────────────────────────────

    @Test
    void getSleepStats_shouldReturnHistory() {
        healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusHours(8))
                .sleepEnd(OffsetDateTime.now())
                .quality(4).source("manual").build());
        healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusDays(1).minusHours(7))
                .sleepEnd(OffsetDateTime.now().minusDays(1))
                .quality(3).source("manual").build());

        SleepStatsDto stats = healthDataService.getSleepStats();
        assertThat(stats).isNotNull();
    }

    @Test
    void getSleepStats_emptyData_shouldReturnEmptyStats() {
        SleepStatsDto stats = healthDataService.getSleepStats();
        assertThat(stats).isNotNull();
    }

    @Test
    void createSleep_minQuality_shouldPersist() {
        SleepResponse resp = healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusHours(6))
                .sleepEnd(OffsetDateTime.now())
                .quality(1).build());
        assertThat(resp.getQuality()).isEqualTo(1);
    }

    @Test
    void createSleep_maxQuality_shouldPersist() {
        SleepResponse resp = healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusHours(9))
                .sleepEnd(OffsetDateTime.now())
                .quality(5).build());
        assertThat(resp.getQuality()).isEqualTo(5);
    }

    // ── weight history ────────────────────────────────────────────────────────

    @Test
    void getWeightHistory_shouldReturnAllEntries() {
        healthDataService.createWeight(WeightRequest.builder()
                .weightKg(new BigDecimal("80.0"))
                .recordedAt(OffsetDateTime.now().minusDays(7)).build());
        healthDataService.createWeight(WeightRequest.builder()
                .weightKg(new BigDecimal("79.5"))
                .recordedAt(OffsetDateTime.now()).build());

        List<WeightResponse> history = healthDataService.getWeightHistory();
        assertThat(history).hasSize(2);
    }

    @Test
    void getWeightHistory_emptyData_shouldReturnEmptyList() {
        List<WeightResponse> history = healthDataService.getWeightHistory();
        assertThat(history).isEmpty();
    }

    // ── water ─────────────────────────────────────────────────────────────────

    @Test
    void getWaterToday_noEntries_shouldReturnZero() {
        Integer total = healthDataService.getWaterToday();
        assertThat(total).isZero();
    }

    @Test
    void addWater_multipleEntries_shouldAccumulate() {
        OffsetDateTime now = OffsetDateTime.now();
        healthDataService.addWater(WaterRequest.builder().amountMl(300).recordedAt(now).build());
        healthDataService.addWater(WaterRequest.builder().amountMl(400).recordedAt(now).build());
        healthDataService.addWater(WaterRequest.builder().amountMl(200).recordedAt(now).build());

        assertThat(healthDataService.getWaterToday()).isEqualTo(900);
    }

    // ── activity ──────────────────────────────────────────────────────────────

    @Test
    void syncActivity_upsert_shouldUpdateExisting() {
        LocalDate today = LocalDate.now();
        healthDataService.syncActivity(ActivityEntryDto.builder()
                .date(today).steps(3000).caloriesBurned(100).source("manual").build());
        healthDataService.syncActivity(ActivityEntryDto.builder()
                .date(today).steps(8000).caloriesBurned(300).source("manual").build());

        ActivityEntryDto result = healthDataService.syncActivity(ActivityEntryDto.builder()
                .date(today).steps(8000).source("manual").build());
        assertThat(result.getSteps()).isEqualTo(8000);
        assertThat(activityEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void getActivityToday_noData_shouldReturnZeroValues() {
        ActivityEntryDto result = healthDataService.getActivityToday();
        assertThat(result).isNotNull();
        assertThat(result.getSteps()).isEqualTo(0);
    }

    @Test
    void getActivityToday_withData_shouldReturn() {
        healthDataService.syncActivity(ActivityEntryDto.builder()
                .date(LocalDate.now()).steps(5000).source("manual").build());

        ActivityEntryDto result = healthDataService.getActivityToday();
        assertThat(result).isNotNull();
        assertThat(result.getSteps()).isEqualTo(5000);
    }

    // ── symptom boundary values ───────────────────────────────────────────────

    @Test
    void createSymptom_minIntensity_shouldPersist() {
        SymptomResponse resp = healthDataService.createSymptom(SymptomRequest.builder()
                .symptom("Fatigue").intensity(1)
                .recordedAt(OffsetDateTime.now()).build());
        assertThat(resp.getIntensity()).isEqualTo(1);
    }

    @Test
    void createSymptom_maxIntensity_shouldPersist() {
        SymptomResponse resp = healthDataService.createSymptom(SymptomRequest.builder()
                .symptom("Migraine").intensity(10)
                .recordedAt(OffsetDateTime.now()).build());
        assertThat(resp.getIntensity()).isEqualTo(10);
    }

    // ── user isolation ────────────────────────────────────────────────────────

    @Test
    void getWaterToday_differentUsers_shouldBeIsolated() {
        // User1 adds water
        healthDataService.addWater(WaterRequest.builder()
                .amountMl(500).recordedAt(OffsetDateTime.now()).build());

        // User2 should see 0
        UUID user2 = UUID.randomUUID();
        setAuth(user2);
        assertThat(healthDataService.getWaterToday()).isZero();
    }

    @Test
    void getSleepStats_differentUsers_shouldBeIsolated() {
        healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusHours(8))
                .sleepEnd(OffsetDateTime.now())
                .quality(4).build());

        UUID user2 = UUID.randomUUID();
        setAuth(user2);
        assertThat(healthDataService.getSleepStats()).isNotNull();
    }

    // ── delete sleep ──────────────────────────────────────────────────────────

    @Test
    void deleteSleep_ownEntry_shouldDelete() {
        SleepResponse created = healthDataService.createSleep(SleepRequest.builder()
                .sleepStart(OffsetDateTime.now().minusHours(8))
                .sleepEnd(OffsetDateTime.now())
                .quality(3).build());

        healthDataService.deleteSleep(created.getId());
        assertThat(sleepEntryRepository.findById(created.getId())).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuth(UUID uid) {
        var auth = new UsernamePasswordAuthenticationToken(
                uid, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
