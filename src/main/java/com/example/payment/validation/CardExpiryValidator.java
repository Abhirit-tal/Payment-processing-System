package com.example.payment.validation;

import com.example.payment.dto.PaymentRequests;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.YearMonth;

public class CardExpiryValidator implements ConstraintValidator<ValidCardExpiry, PaymentRequests.Card> {

    @Override
    public void initialize(ValidCardExpiry constraintAnnotation) { }

    @Override
    public boolean isValid(PaymentRequests.Card card, ConstraintValidatorContext context) {
        if (card == null) return false;
        int month = card.getExpMonth();
        int year = card.getExpYear();
        if (month < 1 || month > 12) return false;
        try {
            YearMonth exp = YearMonth.of(year, month);
            YearMonth now = YearMonth.now();
            if (exp.isBefore(now)) return false;
        } catch (Exception ex) {
            return false;
        }

        // CVV validation: digits only and length depends on card brand
        String cvv = card.getCvv();
        if (cvv == null || !cvv.matches("\\d{3,4}")) return false;

        String number = card.getNumber();
        if (number == null) return false;
        String clean = number.replaceAll("\\s+", "");
        // Amex cards start with 34 or 37
        boolean isAmex = clean.startsWith("34") || clean.startsWith("37");
        if (isAmex) {
            return cvv.length() == 4;
        } else {
            return cvv.length() == 3;
        }
    }
}
