package com.healthlife.mental.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "breathing_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreathingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String technique;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column
    private OffsetDateTime recordedAt;
}
