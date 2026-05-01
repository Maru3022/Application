package com.healthlife.social.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(name = "target_value")
    private Integer targetValue;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
