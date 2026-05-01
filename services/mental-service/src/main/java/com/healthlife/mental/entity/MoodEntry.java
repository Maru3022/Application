package com.healthlife.mental.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "mood_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    @Column
    private String emotions;

    @Column
    private String note;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
