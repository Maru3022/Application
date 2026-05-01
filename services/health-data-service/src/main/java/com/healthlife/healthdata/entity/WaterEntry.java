package com.healthlife.healthdata.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "water_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(name = "amount_ml", nullable = false)
    private Integer amountMl;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;
}
