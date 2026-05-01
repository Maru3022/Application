package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.SleepEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SleepEntryRepository extends JpaRepository<SleepEntry, UUID> {
    List<SleepEntry> findByUserIdAndSleepStartBetweenOrderBySleepStartDesc(UUID userId, OffsetDateTime from, OffsetDateTime to);
    List<SleepEntry> findByUserIdOrderBySleepStartDesc(UUID userId);
}
