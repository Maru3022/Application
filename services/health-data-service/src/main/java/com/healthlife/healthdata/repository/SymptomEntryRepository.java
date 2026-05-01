package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.SymptomEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymptomEntryRepository extends JpaRepository<SymptomEntry, UUID> {
    List<SymptomEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
