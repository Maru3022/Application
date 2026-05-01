package com.healthlife.mental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stress_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StressEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;

    @Column
    private String notes;
}
