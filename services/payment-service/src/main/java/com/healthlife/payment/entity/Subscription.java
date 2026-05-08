package com.healthlife.payment.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** Stripe customer ID (cus_xxx). */
    @Column(unique = true)
    private String stripeCustomerId;

    /** Stripe subscription ID (sub_xxx). Null for free tier. */
    @Column(unique = true)
    private String stripeSubscriptionId;

    /** Stripe price ID (price_xxx) — identifies the plan. */
    private String stripePriceId;

    /**
     * Plan name: FREE, PRO, PREMIUM.
     * Kept in sync with Stripe via webhook events.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String plan = "FREE";

    /**
     * Subscription status mirroring Stripe: active, trialing, past_due,
     * canceled, unpaid, incomplete, incomplete_expired, paused.
     */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "active";

    private OffsetDateTime currentPeriodStart;
    private OffsetDateTime currentPeriodEnd;
    private OffsetDateTime canceledAt;

    @CreationTimestamp
    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
