package com.healthlife.healthdata.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "activity_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private java.time.LocalDate date;

    @Builder.Default
    private Integer steps = 0;

    @Column(name = "calories_burned")
    @Builder.Default
    private Integer caloriesBurned = 0;

    @Column(name = "active_minutes")
    @Builder.Default
    private Integer activeMinutes = 0;

    @Column(name = "distance_m")
    @Builder.Default
    private Integer distanceM = 0;

    @Column(length = 30)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
