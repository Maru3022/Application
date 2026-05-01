package com.healthlife.mental.repository;

import com.healthlife.mental.entity.StressEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StressEntryRepository extends JpaRepository<StressEntry, UUID> {
    List<StressEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
