package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.CycleEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CycleEntryRepository extends JpaRepository<CycleEntry, UUID> {
    List<CycleEntry> findByUserIdOrderByPeriodStartDesc(UUID userId);

    Page<CycleEntry> findByUserIdOrderByPeriodStartDesc(UUID userId, Pageable pageable);
}
