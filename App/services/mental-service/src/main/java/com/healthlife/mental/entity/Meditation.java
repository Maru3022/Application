package com.healthlife.mental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "meditations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Meditation {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column
    private String category;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;
}
