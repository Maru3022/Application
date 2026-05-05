package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.WeightEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    List<WeightEntry> findByUserIdOrderByRecordedAtDesc(UUID userId);

    org.springframework.data.domain.Page<WeightEntry> findByUserIdOrderByRecordedAtDesc(
            UUID userId, org.springframework.data.domain.Pageable pageable);
}
