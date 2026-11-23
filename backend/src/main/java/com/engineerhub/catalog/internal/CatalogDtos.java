package com.engineerhub.catalog.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CatalogDtos {

  static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";
  static final String DIFFICULTY = "^(beginner|intermediate|advanced)$";
  static final String TIER = "^(free|premium)$";

  private CatalogDtos() {}

  // ---------------------------------------------------------------- requests

  record CourseCreate(
      @NotBlank
          @Size(max = 80)
          @Pattern(regexp = SLUG, message = "lowercase letters, digits and single hyphens")
          String slug,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 2000) String description,
      UUID topicId,
      @NotBlank @Pattern(regexp = DIFFICULTY) String difficulty,
      @NotBlank @Pattern(regexp = TIER) String accessTier) {}

  /** Partial update; null = unchanged. {@code expectedVersion} guards against lost updates. */
  record CoursePatch(
      @Size(min = 1, max = 200) String title,
      @Size(max = 2000) String description,
      UUID topicId,
      @Pattern(regexp = DIFFICULTY) String difficulty,
      @Pattern(regexp = TIER) String accessTier,
      @Min(1) @Max(100_000) Integer estMinutes,
      @NotNull Integer expectedVersion) {}

  record SectionCreate(@NotBlank @Size(max = 200) String title) {}

  record SectionPatch(@NotBlank @Size(max = 200) String title) {}

  record OrderRequest(@NotNull @Size(max = 500) List<UUID> ids) {}

  record LessonCreate(
      @NotNull UUID sectionId,
      @NotBlank
          @Size(max = 80)
          @Pattern(regexp = SLUG, message = "lowercase letters, digits and single hyphens")
          String slug,
      @NotBlank @Size(max = 200) String title,
      @Pattern(regexp = TIER) String accessTier) {}

  record LessonPatch(
      @Size(min = 1, max = 200) String title,
      @Pattern(regexp = TIER) String accessTier,
      Boolean freePreview,
      UUID sectionId,
      @NotNull Integer expectedVersion) {}

  record BlockInput(@NotBlank String type, @NotNull Map<String, Object> data) {}

  record BlocksRequest(
      @NotNull @Size(max = 200) List<@NotNull BlockInput> blocks,
      @NotNull Integer expectedVersion) {}

  record SourceRequest(
      @NotBlank @Pattern(regexp = "^(original|licensed|open_license|ai_assisted)$") String kind,
      @Size(max = 60) String license,
      @Size(max = 300) String attribution,
      @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$", message = "must be an https URL")
          String sourceUrl) {}

  record PublishRequest(boolean originalityConfirmed) {}

  // ---------------------------------------------------------------- views

  record LessonSummary(
      UUID id,
      String slug,
      String title,
      int position,
      String accessTier,
      boolean freePreview,
      String status,
      int version,
      boolean hasUnpublishedChanges) {}

  record SectionView(UUID id, String title, int position, List<LessonSummary> lessons) {}

  record CourseView(
      UUID id,
      String slug,
      String title,
      String description,
      UUID topicId,
      String difficulty,
      String accessTier,
      String status,
      Integer estMinutes,
      int version,
      Instant publishedAt,
      Instant createdAt,
      Instant updatedAt,
      List<SectionView> sections) {}

  record CourseListItem(
      UUID id,
      String slug,
      String title,
      String status,
      String accessTier,
      String difficulty,
      Instant updatedAt) {}

  record BlockView(String type, int schemaVersion, Map<String, Object> data) {}

  record SourceView(
      String kind,
      String license,
      String attribution,
      String sourceUrl,
      UUID reviewedBy,
      Instant reviewedAt) {}

  record LessonView(
      UUID id,
      UUID courseId,
      UUID sectionId,
      String slug,
      String title,
      int position,
      String accessTier,
      boolean freePreview,
      Integer estReadMin,
      String status,
      int version,
      Instant publishedAt,
      boolean hasUnpublishedChanges,
      List<BlockView> blocks,
      SourceView source) {}

  record TopicView(UUID id, String slug, String name, int position) {}
}
