package com.healthlife.healthdata.repository;

import com.healthlife.healthdata.entity.ActivityEntry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEntryRepository extends JpaRepository<ActivityEntry, UUID> {
    Optional<ActivityEntry> findByUserIdAndDate(UUID userId, LocalDate date);
}
