package com.healthlife.social.repository;

import com.healthlife.social.entity.ChallengeParticipant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, UUID> {

    List<ChallengeParticipant> findByChallengeId(UUID challengeId);

    boolean existsByChallengeIdAndUserId(UUID challengeId, UUID userId);

    long countByChallengeId(UUID challengeId);

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(UUID challengeId, UUID userId);

    // FIX N+1: bulk count per challenge — replaces N individual countByChallengeId calls
    @Query("SELECT cp.challengeId, COUNT(cp) FROM ChallengeParticipant cp"
            + " WHERE cp.challengeId IN :ids GROUP BY cp.challengeId")
    List<Object[]> countByChallengeIdIn(@Param("ids") List<UUID> ids);

    // FIX N+1: bulk existence check — replaces N individual existsByChallengeIdAndUserId calls
    @Query("SELECT cp.challengeId FROM ChallengeParticipant cp"
            + " WHERE cp.challengeId IN :ids AND cp.userId = :userId")
    Set<UUID> findJoinedChallengeIds(@Param("ids") List<UUID> ids, @Param("userId") UUID userId);
}
