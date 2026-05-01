package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    List<WeightEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);
}
