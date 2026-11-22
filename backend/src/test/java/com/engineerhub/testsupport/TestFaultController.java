package com.engineerhub.testsupport;

import com.engineerhub.shared.error.ApiException;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that provoke each error path. Exists only on the test classpath. */
@RestController
@RequestMapping("/test-support")
public class TestFaultController {

  public record Payload(
      @NotBlank String name,
      @Min(1) int age,
      @Size(min = 20, message = "must be at least 20 characters") String secretCode) {}

  @GetMapping("/api-exception")
  @PermitAll
  Map<String, String> apiException() {
    throw ApiException.notFound("Course not found.");
  }

  @GetMapping("/boom")
  @PermitAll
  Map<String, String> boom() {
    throw new IllegalStateException(
        "secret-internal-detail jdbc:postgresql://db/prod password=hunter2");
  }

  @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PermitAll
  Map<String, String> validate(@Valid @RequestBody Payload payload) {
    return Map.of("ok", "true");
  }

  @GetMapping("/admin-only")
  @PreAuthorize("hasRole('ADMIN')")
  Map<String, String> adminOnly() {
    return Map.of("ok", "true");
  }
}
