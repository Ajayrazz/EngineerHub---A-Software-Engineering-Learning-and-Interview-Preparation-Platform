package com.engineerhub.identity.internal;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

/** A canonical IANA time-zone id known to the JVM (e.g. {@code Asia/Kolkata}). */
@Documented
@Constraint(validatedBy = IanaTimezone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface IanaTimezone {
  String message() default "must be a valid IANA time zone, e.g. Asia/Kolkata";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  final class Validator implements ConstraintValidator<IanaTimezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value != null && ZoneId.getAvailableZoneIds().contains(value);
    }
  }
}
