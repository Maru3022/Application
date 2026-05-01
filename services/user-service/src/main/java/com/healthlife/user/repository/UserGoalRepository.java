package com.healthlife.user.repository;

import com.healthlife.user.entity.UserGoal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
    Optional<UserGoal> findByUserId(UUID userId);
}
