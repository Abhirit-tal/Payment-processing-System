package com.example.payment.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentRequestsValidationTest {

    private static ValidatorFactory vf;
    private static Validator validator;

    @BeforeAll
    public static void setup() {
        vf = Validation.buildDefaultValidatorFactory();
        validator = vf.getValidator();
    }

    @AfterAll
    public static void teardown() {
        vf.close();
    }

    @Test
    public void validPurchaseRequest() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("4111111111111111");
        card.setExpMonth(12);
        card.setExpYear(2030);
        card.setCvv("123");

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var violations = validator.validate(req);
        assertTrue(violations.isEmpty(), () -> "Expected no violations but got: " + violations);
    }

    @Test
    public void invalidExpiryFails() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("4111111111111111");
        card.setExpMonth(1);
        card.setExpYear(2000); // expired
        card.setCvv("123");

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void invalidCvvFails() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("4111111111111111");
        card.setExpMonth(12);
        card.setExpYear(2030);
        card.setCvv("12"); // too short

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }
}

