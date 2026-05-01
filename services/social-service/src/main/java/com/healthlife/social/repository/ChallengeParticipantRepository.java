package com.healthlife.social.repository;

import com.healthlife.social.entity.ChallengeParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, UUID> {
    List<ChallengeParticipant> findByChallengeId(UUID challengeId);

    boolean existsByChallengeIdAndUserId(UUID challengeId, UUID userId);

    long countByChallengeId(UUID challengeId);
}
