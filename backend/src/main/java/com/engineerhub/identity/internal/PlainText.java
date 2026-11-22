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

/** Plain text without control characters (and optionally without line breaks). */
@Documented
@Constraint(validatedBy = PlainText.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface PlainText {
  String message() default "must be plain text without control characters";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  boolean allowNewlines() default false;

  final class Validator implements ConstraintValidator<PlainText, String> {
    private boolean newlines;

    @Override
    public void initialize(PlainText annotation) {
      this.newlines = annotation.allowNewlines();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      return value
          .chars()
          .noneMatch(
              c ->
                  (Character.isISOControl(c) && !(newlines && (c == '\n')))
                      || c == 0x2028
                      || c == 0x2029);
    }
  }
}
