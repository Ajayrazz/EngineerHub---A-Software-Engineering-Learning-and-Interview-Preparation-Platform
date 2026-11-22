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
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/**
 * A user-supplied link that will be rendered as {@code <a href>} on public profiles, so it must
 * never be {@code javascript:}/{@code data:} or a credential-bearing URL: https only, no user-info,
 * an optional host allowlist, bounded length, no whitespace/control characters. Empty means "clear
 * the field".
 */
@Documented
@Constraint(validatedBy = SafeUrl.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface SafeUrl {
  String message() default "must be a valid https URL";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /** If non-empty, the URL host must equal one of these (case-insensitive). */
  String[] hosts() default {};

  final class Validator implements ConstraintValidator<SafeUrl, String> {
    private String[] hosts;

    @Override
    public void initialize(SafeUrl annotation) {
      this.hosts = annotation.hosts();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null || value.isEmpty()) {
        return true;
      }
      if (value.length() > 300 || value.chars().anyMatch(c -> c <= 0x20 || c == 0x7f)) {
        return false;
      }
      try {
        URI uri = new URI(value);
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null) {
          return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || (uri.getPort() != -1 && uri.getPort() != 443)) {
          return false;
        }
        if (hosts.length > 0) {
          String h = host.toLowerCase(Locale.ROOT);
          return Arrays.stream(hosts).anyMatch(allowed -> allowed.equalsIgnoreCase(h));
        }
        return true;
      } catch (java.net.URISyntaxException e) {
        return false;
      }
    }
  }
}
