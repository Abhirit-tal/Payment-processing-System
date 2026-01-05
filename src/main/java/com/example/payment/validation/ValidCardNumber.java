package com.example.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = CardNumberValidator.class)
@Target({ FIELD })
@Retention(RUNTIME)
public @interface ValidCardNumber {
    String message() default "invalid card number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

