package com.healthlife.mental.repository;

import com.healthlife.mental.entity.Meditation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MeditationRepository extends JpaRepository<Meditation, UUID> {
    List<Meditation> findByCategory(String category);
}
