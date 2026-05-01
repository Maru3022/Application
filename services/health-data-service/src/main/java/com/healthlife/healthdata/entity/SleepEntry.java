package com.healthlife.healthdata.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "sleep_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SleepEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(name = "sleep_start", nullable = false)
    private OffsetDateTime sleepStart;

    @Column(name = "sleep_end", nullable = false)
    private OffsetDateTime sleepEnd;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column
    private Integer quality;

    @Column
    private String notes;

    @Column(length = 30)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void calculateDuration() {
        if (sleepStart != null && sleepEnd != null) {
            this.durationMin =
                    (int) java.time.Duration.between(sleepStart, sleepEnd).toMinutes();
        }
    }
}
