package com.healthlife.mental.repository;

import com.healthlife.mental.entity.StressEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StressEntryRepository extends JpaRepository<StressEntry, UUID> {
    List<StressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
