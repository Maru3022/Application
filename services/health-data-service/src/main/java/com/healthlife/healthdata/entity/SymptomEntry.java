package com.healthlife.healthdata.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "symptom_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymptomEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String symptom;

    private Integer intensity;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;

    @Column
    private String notes;
}
