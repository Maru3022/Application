package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.SymptomEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SymptomEntryRepository extends JpaRepository<SymptomEntry, UUID> {
    List<SymptomEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
