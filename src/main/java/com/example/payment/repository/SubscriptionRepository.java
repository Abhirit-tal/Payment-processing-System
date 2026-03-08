package com.example.payment.repository;

import com.example.payment.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);
    List<Subscription> findByStatus(String status);

    /**
     * Find active subscriptions that are due for billing (next_billing_date <= today).
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'active' " +
           "AND s.nextBillingDate IS NOT NULL " +
           "AND s.nextBillingDate <= :today " +
           "ORDER BY s.nextBillingDate ASC")
    List<Subscription> findSubscriptionsDueForBilling(LocalDate today);
}

