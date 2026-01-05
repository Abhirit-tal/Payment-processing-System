package com.example.payment.validation;

import com.example.payment.dto.PaymentRequests;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CardBrandCvvValidationTest {

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
    public void amexRequires4DigitCvv() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("340000000000009"); // Amex test number
        card.setExpMonth(12);
        card.setExpYear(2030);
        card.setCvv("1234");

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var v = validator.validate(req);
        assertTrue(v.isEmpty(), "AMEX with 4-digit CVV should be valid");
    }

    @Test
    public void amexWith3DigitCvvIsInvalid() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("370000000000002");
        card.setExpMonth(12);
        card.setExpYear(2030);
        card.setCvv("123");

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var v = validator.validate(req);
        assertFalse(v.isEmpty(), "AMEX with 3-digit CVV should be invalid");
    }

    @Test
    public void visaAllows3DigitCvv() {
        PaymentRequests.Card card = new PaymentRequests.Card();
        card.setNumber("4111111111111111");
        card.setExpMonth(12);
        card.setExpYear(2030);
        card.setCvv("123");

        PaymentRequests.PurchaseRequest req = new PaymentRequests.PurchaseRequest();
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("USD");
        req.setCard(card);

        var v = validator.validate(req);
        assertTrue(v.isEmpty(), "Visa with 3-digit CVV should be valid");
    }
}

