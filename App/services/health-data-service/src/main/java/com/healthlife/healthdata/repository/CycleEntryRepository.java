package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.CycleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CycleEntryRepository extends JpaRepository<CycleEntry, UUID> {
    List<CycleEntry> findByUserIdOrderByPeriodStartDesc(UUID userId);
}
