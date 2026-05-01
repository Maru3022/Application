package com.healthlife.healthdata.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "cycle_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "cycle_length")
    private Integer cycleLength;

    @Column(name = "flow_intensity", length = 20)
    private String flowIntensity;

    @Column
    private String notes;
}
