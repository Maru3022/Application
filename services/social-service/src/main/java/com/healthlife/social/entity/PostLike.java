package com.healthlife.social.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"post_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID postId;

    @Column(nullable = false)
    private UUID userId;
}
