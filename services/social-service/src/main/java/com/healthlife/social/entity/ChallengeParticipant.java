package com.healthlife.social.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "challenge_participants",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"challenge_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChallengeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID challengeId;

    @Column(nullable = false)
    private UUID userId;

    @Column
    private Integer progress;
}
