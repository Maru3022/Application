package com.healthlife.social.repository;

import com.healthlife.social.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}
