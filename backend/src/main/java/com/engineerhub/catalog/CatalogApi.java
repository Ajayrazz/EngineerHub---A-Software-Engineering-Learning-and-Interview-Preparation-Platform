package com.engineerhub.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the catalog module for other modules (learning, later search/analytics): read-only
 * facts about <em>published</em> content. Drafts, archived and unpublished things are simply
 * absent, so callers can never act on them by accident.
 */
public interface CatalogApi {

  record PublishedCourse(UUID id, String slug, String title) {}

  record PublishedLesson(
      UUID id,
      UUID courseId,
      String courseSlug,
      String slug,
      String title,
      String accessTier,
      boolean freePreview) {}

  Optional<PublishedCourse> publishedCourse(UUID courseId);

  Optional<PublishedLesson> publishedLesson(UUID lessonId);

  /**
   * Published lessons of a published course in reading order (empty if the course is not
   * published).
   */
  List<PublishedLesson> publishedLessons(UUID courseId);

  Map<UUID, PublishedCourse> publishedCourses(Collection<UUID> courseIds);
}
