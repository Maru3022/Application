package com.healthlife.social.repository;

import com.healthlife.social.entity.PostLike;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Modifying
    @Transactional
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}
