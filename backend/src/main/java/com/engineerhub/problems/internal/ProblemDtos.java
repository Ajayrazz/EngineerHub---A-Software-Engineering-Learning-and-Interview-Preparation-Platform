package com.engineerhub.problems.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ProblemDtos {

  static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";
  static final String DIFFICULTY = "^(easy|medium|hard)$";
  static final String TIER = "^(free|premium)$";
  static final String CHECKER = "^(exact|token|float)$";

  private ProblemDtos() {}

  // ---------------------------------------------------------------- requests

  record ProblemCreate(
      @NotBlank
          @Size(max = 80)
          @Pattern(regexp = SLUG, message = "lowercase letters, digits and single hyphens")
          String slug,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Pattern(regexp = DIFFICULTY) String difficulty,
      @NotBlank @Pattern(regexp = TIER) String accessTier) {}

  /** Partial update; null = unchanged. {@code expectedVersion} guards against lost updates. */
  record ProblemPatch(
      @Size(min = 1, max = 200) String title,
      @Size(max = 50_000) String statement,
      @Size(max = 5_000) String inputFormat,
      @Size(max = 5_000) String outputFormat,
      @Size(max = 5_000) String constraints,
      @Size(max = 5) List<@NotNull @Size(min = 1, max = 2_000) String> hints,
      @Min(100) @Max(20_000) Integer timeLimitMs,
      @Min(16) @Max(2_048) Integer memoryLimitMb,
      @Pattern(regexp = CHECKER) String checkerType,
      @Pattern(regexp = DIFFICULTY) String difficulty,
      @Pattern(regexp = TIER) String accessTier,
      @NotNull Integer expectedVersion) {}

  record TagsRequest(
      @NotNull @Size(max = 10) List<UUID> topicIds,
      @NotNull @Size(max = 10) List<UUID> patternIds) {}

  record TestInput(
      @NotNull @Size(max = 100_000) String input,
      @NotNull @Size(max = 100_000) String expected,
      boolean sample,
      @Size(max = 2_000) String explanation) {}

  record TestsRequest(
      @NotNull @Size(max = 200) List<@NotNull TestInput> tests,
      @NotNull Integer expectedTestcaseVersion) {}

  record PatternCreate(
      @NotBlank
          @Size(max = 60)
          @Pattern(regexp = SLUG, message = "lowercase letters, digits and single hyphens")
          String slug,
      @NotBlank @Size(max = 100) String name) {}

  record ProblemSourceRequest(
      @NotBlank @Pattern(regexp = "^(original|licensed|open_license|ai_assisted)$") String kind,
      @Size(max = 60) String license,
      @Size(max = 300) String attribution,
      @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$", message = "must be an https URL")
          String sourceUrl) {}

  record ProblemPublishRequest(boolean originalityConfirmed) {}

  // ---------------------------------------------------------------- views

  record TagView(UUID id, String slug, String name) {}

  record ProblemSourceView(
      String kind,
      String license,
      String attribution,
      String sourceUrl,
      UUID reviewedBy,
      Instant reviewedAt) {}

  record ProblemView(
      UUID id,
      String slug,
      String title,
      String status,
      String difficulty,
      String accessTier,
      String statement,
      String inputFormat,
      String outputFormat,
      String constraints,
      List<String> hints,
      int timeLimitMs,
      int memoryLimitMb,
      String checkerType,
      int version,
      int testcaseVersion,
      int sampleCount,
      int hiddenCount,
      String validationStatus,
      Instant publishedAt,
      Instant updatedAt,
      boolean hasUnpublishedChanges,
      List<TagView> topics,
      List<TagView> patterns,
      ProblemSourceView source) {}

  record ProblemListItem(
      UUID id,
      String slug,
      String title,
      String status,
      String difficulty,
      String accessTier,
      String validationStatus,
      Instant updatedAt) {}

  record TestView(int ordinal, boolean sample, String input, String expected, String explanation) {}

  record TestsView(int testcaseVersion, String validationStatus, List<TestView> tests) {}
}
