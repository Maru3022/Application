package com.healthlife.social.repository;

import com.healthlife.social.entity.Challenge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {}
