package com.healthlife.mental.repository;

import com.healthlife.mental.entity.BreathingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BreathingSessionRepository extends JpaRepository<BreathingSession, UUID> {
}
