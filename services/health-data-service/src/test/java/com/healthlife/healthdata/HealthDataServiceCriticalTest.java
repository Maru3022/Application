package com.healthlife.healthdata;

import static org.assertj.core.api.Assertions.*;

import com.healthlife.common.dto.health.*;
import com.healthlife.common.exception.ForbiddenException;
import com.healthlife.common.exception.ResourceNotFoundException;
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

@SpringBootTest(classes = HealthDataServiceApplication.class)
@ActiveProfiles("test")
class HealthDataServiceCriticalTest {

    @Autowired
    private HealthDataService healthDataService;

    @Autowired
    private SleepEntryRepository sleepEntryRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private WaterEntryRepository waterEntryRepository;

    @Autowired
    private ActivityEntryRepository activityEntryRepository;

    @Autowired
    private SymptomEntryRepository symptomEntryRepository;

    @Autowired
    private CycleEntryRepository cycleEntryRepository;

    private UUID userId;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@health.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        sleepEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
        waterEntryRepository.deleteAll();
        activityEntryRepository.deleteAll();
        symptomEntryRepository.deleteAll();
        cycleEntryRepository.deleteAll();
    }

    @Test
    void createSleep_shouldCalculateDuration() {
        OffsetDateTime start = OffsetDateTime.now().minusHours(8);
        OffsetDateTime end = OffsetDateTime.now();
        SleepRequest req = SleepRequest.builder()
                .sleepStart(start)
                .sleepEnd(end)
                .quality(4)
                .source("manual")
                .build();

        SleepResponse resp = healthDataService.createSleep(req);
        assertThat(resp.getDurationMin()).isGreaterThanOrEqualTo(480);
        assertThat(resp.getQuality()).isEqualTo(4);
    }

    @Test
    void createSleep_endBeforeStart_shouldCalculateNegativeDuration() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = OffsetDateTime.now().minusHours(8);
        SleepRequest req = SleepRequest.builder()
                .sleepStart(start)
                .sleepEnd(end)
                .quality(3)
                .build();

        SleepResponse resp = healthDataService.createSleep(req);
        // Duration will be negative - this is a potential bug case
        assertThat(resp.getDurationMin()).isNegative();
    }

    @Test
    void deleteSleep_otherUserEntry_shouldThrowForbidden() {
        UUID otherUserId = UUID.randomUUID();
        SleepEntry entry = SleepEntry.builder()
                .userId(otherUserId)
                .sleepStart(OffsetDateTime.now().minusHours(8))
                .sleepEnd(OffsetDateTime.now())
                .build();
        entry = sleepEntryRepository.save(entry);
        UUID entryId = entry.getId();

        assertThatThrownBy(() -> healthDataService.deleteSleep(entryId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteSleep_nonExistent_shouldThrowNotFound() {
        assertThatThrownBy(() -> healthDataService.deleteSleep(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWeight_shouldPersist() {
        WeightRequest req = WeightRequest.builder()
                .weightKg(new BigDecimal("75.5"))
                .bodyFatPct(new BigDecimal("18.2"))
                .recordedAt(OffsetDateTime.now())
                .build();
        WeightResponse resp = healthDataService.createWeight(req);
        assertThat(resp.getWeightKg()).isEqualByComparingTo(new BigDecimal("75.5"));
    }

    @Test
    void addWater_andGetToday_shouldSum() {
        OffsetDateTime now = OffsetDateTime.now();
        healthDataService.addWater(
                WaterRequest.builder().amountMl(250).recordedAt(now).build());
        healthDataService.addWater(
                WaterRequest.builder().amountMl(500).recordedAt(now).build());

        Integer total = healthDataService.getWaterToday();
        assertThat(total).isEqualTo(750);
    }

    @Test
    void createSymptom_shouldPersist() {
        SymptomRequest req = SymptomRequest.builder()
                .symptom("Headache")
                .intensity(7)
                .recordedAt(OffsetDateTime.now())
                .notes("After lunch")
                .build();
        SymptomResponse resp = healthDataService.createSymptom(req);
        assertThat(resp.getSymptom()).isEqualTo("Headache");
        assertThat(resp.getIntensity()).isEqualTo(7);
    }

    @Test
    void createCycle_andPredictNext_shouldReturnPrediction() {
        healthDataService.createCycle(CycleRequest.builder()
                .periodStart(LocalDate.now().minusDays(28))
                .periodEnd(LocalDate.now().minusDays(23))
                .cycleLength(28)
                .flowIntensity("medium")
                .build());

        healthDataService.createCycle(CycleRequest.builder()
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now().plusDays(5))
                .cycleLength(28)
                .flowIntensity("medium")
                .build());

        CycleResponse prediction = healthDataService.getCyclePrediction();
        assertThat(prediction.getCycleLength()).isEqualTo(28);
        assertThat(prediction.getPeriodStart()).isNotNull();
    }

    @Test
    void getCyclePrediction_noData_shouldThrow() {
        assertThatThrownBy(() -> healthDataService.getCyclePrediction()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void syncActivity_shouldUpsert() {
        ActivityEntryDto dto = ActivityEntryDto.builder()
                .date(LocalDate.now())
                .steps(5000)
                .caloriesBurned(200)
                .source("manual")
                .build();
        ActivityEntryDto result = healthDataService.syncActivity(dto);
        assertThat(result.getSteps()).isEqualTo(5000);

        ActivityEntryDto update = ActivityEntryDto.builder()
                .date(LocalDate.now())
                .steps(10000)
                .source("manual")
                .build();
        result = healthDataService.syncActivity(update);
        assertThat(result.getSteps()).isEqualTo(10000);
    }
}
