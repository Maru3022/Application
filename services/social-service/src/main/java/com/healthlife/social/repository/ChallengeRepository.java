package com.healthlife.social.repository;

import com.healthlife.social.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
}
