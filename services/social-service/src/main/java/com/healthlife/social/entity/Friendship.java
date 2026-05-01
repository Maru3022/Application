package com.healthlife.social.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "friendships",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "friend_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID friendId;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
