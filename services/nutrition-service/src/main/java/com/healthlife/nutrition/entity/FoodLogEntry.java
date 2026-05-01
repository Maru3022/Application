package com.healthlife.nutrition.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "food_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID foodId;

    @Column(name = "weight_grams", nullable = false)
    private Double weightGrams;

    @Column(name = "meal_type", length = 20)
    private String mealType;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
