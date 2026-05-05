package com.healthlife.mental.repository;

import com.healthlife.mental.entity.MoodEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, UUID> {
    List<MoodEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    org.springframework.data.domain.Page<MoodEntry> findByUserIdOrderByRecordedAtDesc(
            UUID userId, org.springframework.data.domain.Pageable pageable);
}
