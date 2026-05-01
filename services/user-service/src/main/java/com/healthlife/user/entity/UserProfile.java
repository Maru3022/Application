package com.healthlife.user.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID userId;

    @Column(length = 100)
    private String displayName;

    @Column(length = 50)
    private String timezone;

    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    private BigDecimal heightCm;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 20)
    @Builder.Default
    private String subscriptionPlan = "FREE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
