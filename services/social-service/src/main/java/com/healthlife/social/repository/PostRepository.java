package com.healthlife.social.repository;

import com.healthlife.social.entity.Post;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds);

    Page<Post> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds, Pageable pageable);
}
