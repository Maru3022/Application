package com.healthlife.mental.repository;

import com.healthlife.mental.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    List<JournalEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
