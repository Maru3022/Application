package com.healthlife.social.repository;

import com.healthlife.social.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    List<Friendship> findByUserIdAndStatus(UUID userId, String status);
    List<Friendship> findByFriendIdAndStatus(UUID friendId, String status);
}
