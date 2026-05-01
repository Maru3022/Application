package com.healthlife.aicoach.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "ai_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(length = 50)
    private String type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
