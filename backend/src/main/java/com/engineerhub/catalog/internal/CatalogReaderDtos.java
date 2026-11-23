package com.engineerhub.catalog.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (learner-facing) shapes. Never contain drafts, ids of unpublished things or provenance
 * data.
 */
final class CatalogReaderDtos {

  private CatalogReaderDtos() {}

  record PublicTopic(String slug, String name) {}

  record PublicCourse(
      String slug,
      String title,
      String description,
      PublicTopic topic,
      String difficulty,
      String accessTier,
      Integer estMinutes,
      int lessonCount,
      Instant publishedAt) {}

  record PublicCoursePage(List<PublicCourse> items, int page, int size, long total) {}

  record PublicOutlineLesson(
      String slug,
      String title,
      int position,
      String accessTier,
      boolean freePreview,
      Integer estReadMin) {}

  record PublicOutlineSection(String title, int position, List<PublicOutlineLesson> lessons) {}

  record PublicCourseDetail(PublicCourse course, UUID id, List<PublicOutlineSection> sections) {}

  record PublicBlock(String type, Map<String, Object> data) {}

  record PublicLessonRef(String slug, String title) {}

  record PublicLesson(
      String courseSlug,
      String slug,
      String title,
      String accessTier,
      boolean freePreview,
      Integer estReadMin,
      List<PublicBlock> blocks,
      PublicLessonRef previous,
      PublicLessonRef next,
      Instant publishedAt) {}
}
