package com.engineerhub.learning.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class LearningDtos {

  private LearningDtos() {}

  record ProgressUpdate(
      @NotBlank
          @Pattern(
              regexp = "^(in_progress|completed)$",
              message = "must be 'in_progress' or 'completed'")
          String status,
      @Min(0) @Max(100) Integer scrollPct) {}

  record ResumeTarget(UUID lessonId, String lessonSlug, String lessonTitle) {}

  record CourseProgress(
      UUID courseId,
      boolean enrolled,
      int totalLessons,
      int completedLessons,
      int percent,
      boolean completed,
      List<UUID> completedLessonIds,
      ResumeTarget resume,
      Instant enrolledAt) {}

  record EnrollmentView(
      UUID courseId,
      String courseSlug,
      String courseTitle,
      Instant enrolledAt,
      Instant lastActivityAt,
      int totalLessons,
      int completedLessons,
      int percent,
      boolean completed) {}
}
