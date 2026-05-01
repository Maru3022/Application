package com.healthlife.auth.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 50)
    private String timezone;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(name = "height_cm")
    private java.math.BigDecimal heightCm;

    @Column(name = "mfa_secret", length = 100)
    private String mfaSecret;

    @Column(name = "mfa_enabled")
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(length = 20)
    @Builder.Default
    private String role = "USER";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
