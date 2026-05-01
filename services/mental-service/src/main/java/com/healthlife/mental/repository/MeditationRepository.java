package com.healthlife.mental.repository;

import com.healthlife.mental.entity.Meditation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeditationRepository extends JpaRepository<Meditation, UUID> {
    List<Meditation> findByCategory(String category);
}
