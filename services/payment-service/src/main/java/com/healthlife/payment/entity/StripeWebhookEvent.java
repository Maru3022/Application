package com.healthlife.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stripe_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeWebhookEvent {

    @Id
    @Column(length = 64)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
