package com.healthlife.payment.repository;

import com.healthlife.payment.entity.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {}
