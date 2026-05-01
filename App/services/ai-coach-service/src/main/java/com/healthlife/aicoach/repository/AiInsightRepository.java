package com.healthlife.aicoach.repository;

import com.healthlife.aicoach.entity.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {
    List<AiInsight> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, String type);
    List<AiInsight> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
