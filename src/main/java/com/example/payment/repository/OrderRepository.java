package com.example.payment.repository;

import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByExternalId(String externalId);
    List<Order> findByStateAndCreatedAtBefore(PaymentState state, Instant cutoff);
    List<Order> findByState(PaymentState state);
}

