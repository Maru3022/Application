package com.healthlife.social.repository;

import com.healthlife.social.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds);
}
