package com.example.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = CardExpiryValidator.class)
@Target({ TYPE })
@Retention(RUNTIME)
public @interface ValidCardExpiry {
    String message() default "card expired or invalid expiry date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

