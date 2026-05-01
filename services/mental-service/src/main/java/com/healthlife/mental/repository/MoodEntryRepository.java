package com.healthlife.mental.repository;

import com.healthlife.mental.entity.MoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, UUID> {
    List<MoodEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
