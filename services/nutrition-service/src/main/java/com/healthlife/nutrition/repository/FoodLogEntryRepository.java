package com.healthlife.nutrition.repository;

import com.healthlife.nutrition.entity.FoodLogEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodLogEntryRepository extends JpaRepository<FoodLogEntry, UUID> {
    List<FoodLogEntry> findByUserIdAndConsumedAtBetweenOrderByConsumedAtDesc(
            UUID userId, OffsetDateTime start, OffsetDateTime end);

    List<FoodLogEntry> findByUserIdOrderByConsumedAtDesc(UUID userId);
}
