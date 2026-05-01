package com.healthlife.user.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID userId;

    @Column(name = "daily_steps")
    @Builder.Default
    private Integer dailySteps = 10000;

    @Column(name = "sleep_minutes")
    @Builder.Default
    private Integer sleepMinutes = 480;

    @Column(name = "water_ml")
    @Builder.Default
    private Integer waterMl = 2000;

    @Column(name = "target_weight_kg")
    private BigDecimal targetWeightKg;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
