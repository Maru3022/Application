package com.healthlife.user.repository;

import com.healthlife.user.entity.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
    Optional<UserGoal> findByUserId(UUID userId);
}
