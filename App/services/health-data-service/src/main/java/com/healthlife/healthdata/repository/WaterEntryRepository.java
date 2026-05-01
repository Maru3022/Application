package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.WaterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WaterEntryRepository extends JpaRepository<WaterEntry, UUID> {
    List<WaterEntry> findByUserIdAndRecordedAtBetween(UUID userId, OffsetDateTime start, OffsetDateTime end);
}
