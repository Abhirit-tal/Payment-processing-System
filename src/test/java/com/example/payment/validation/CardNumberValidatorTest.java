package com.example.payment.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardNumberValidatorTest {

    private final CardNumberValidator validator = new CardNumberValidator();

    @Test
    public void validCardNumbers() {
        assertTrue(validator.isValid("4111111111111111", null)); // Visa test card
        assertTrue(validator.isValid("4012888888881881", null));
    }

    @Test
    public void invalidCardNumbers() {
        assertFalse(validator.isValid("1234567890123456", null));
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("abcd1234", null));
    }
}

