package com.healthlife.aicoach.repository;

import com.healthlife.aicoach.entity.AiInsight;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {
    List<AiInsight> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, String type);

    List<AiInsight> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
