package com.engineerhub.problems.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ValidationDtos {

  private ValidationDtos() {}

  record ProgramRequest(
      @NotBlank @Pattern(regexp = "^(java|python|cpp)$", message = "must be java, python or cpp")
          String language,
      @NotBlank @Size(max = 64_000) String source) {}

  record RunRequest(@Min(1) @Max(500) int generatedCases) {}

  record ProgramView(
      String role, String language, String source, String sha256, Instant updatedAt) {}

  record RunView(
      UUID id,
      String status,
      int testcaseVersion,
      int generatedCases,
      Integer testsChecked,
      Integer generatedChecked,
      String executor,
      String summary,
      String counterexample,
      Instant requestedAt,
      Instant finishedAt) {}

  record ValidationView(
      String validationStatus,
      int testcaseVersion,
      boolean executorAvailable,
      List<ProgramView> programs,
      List<RunView> runs) {}
}
