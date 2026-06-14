package com.healthlife.social.repository;

import com.healthlife.social.entity.Friendship;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    List<Friendship> findByUserIdAndStatus(UUID userId, String status);

    List<Friendship> findByFriendIdAndStatus(UUID friendId, String status);

    boolean existsByUserIdAndFriendId(UUID userId, UUID friendId);

    Optional<Friendship> findByUserIdAndFriendId(UUID userId, UUID friendId);
}
