package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.WaterEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterEntryRepository extends JpaRepository<WaterEntry, UUID> {
    List<WaterEntry> findByUserIdAndRecordedAtBetween(UUID userId, OffsetDateTime start, OffsetDateTime end);
}
