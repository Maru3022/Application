package com.healthlife.nutrition.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "foods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "calories_per_100g")
    private Double caloriesPer100g;

    @Column(name = "protein_per_100g")
    private Double proteinPer100g;

    @Column(name = "carbs_per_100g")
    private Double carbsPer100g;

    @Column(name = "fat_per_100g")
    private Double fatPer100g;

    @Column
    private String barcode;

    @Column(length = 30)
    @Builder.Default
    private String source = "system";

    @Column(name = "user_id")
    private UUID userId;
}
