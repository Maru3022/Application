package com.healthlife.mental.repository;

import com.healthlife.mental.entity.BreathingSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreathingSessionRepository extends JpaRepository<BreathingSession, UUID> {}
