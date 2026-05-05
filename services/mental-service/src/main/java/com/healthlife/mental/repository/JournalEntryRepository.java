package com.healthlife.mental.repository;

import com.healthlife.mental.entity.JournalEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    List<JournalEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    org.springframework.data.domain.Page<JournalEntry> findByUserIdOrderByRecordedAtDesc(
            UUID userId, org.springframework.data.domain.Pageable pageable);
}
