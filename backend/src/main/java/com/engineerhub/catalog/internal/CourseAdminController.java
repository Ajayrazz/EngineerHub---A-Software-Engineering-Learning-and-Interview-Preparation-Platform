package com.engineerhub.catalog.internal;

import com.engineerhub.catalog.internal.CatalogDtos.BlocksRequest;
import com.engineerhub.catalog.internal.CatalogDtos.CourseCreate;
import com.engineerhub.catalog.internal.CatalogDtos.CourseListItem;
import com.engineerhub.catalog.internal.CatalogDtos.CoursePatch;
import com.engineerhub.catalog.internal.CatalogDtos.CourseView;
import com.engineerhub.catalog.internal.CatalogDtos.LessonCreate;
import com.engineerhub.catalog.internal.CatalogDtos.LessonPatch;
import com.engineerhub.catalog.internal.CatalogDtos.LessonView;
import com.engineerhub.catalog.internal.CatalogDtos.OrderRequest;
import com.engineerhub.catalog.internal.CatalogDtos.PublishRequest;
import com.engineerhub.catalog.internal.CatalogDtos.SectionCreate;
import com.engineerhub.catalog.internal.CatalogDtos.SectionPatch;
import com.engineerhub.catalog.internal.CatalogDtos.SourceRequest;
import com.engineerhub.catalog.internal.CatalogDtos.TopicView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authoring API. Editing a course needs {@code course.edit} on that course (global for editors,
 * scoped for assigned instructors); creating, publishing and archiving need the global,
 * non-grantable {@code course.publish} / {@code course.edit}.
 */
@RestController
@RequestMapping("/api/v1/admin")
class CourseAdminController {

  private final CatalogAdminService service;

  CourseAdminController(CatalogAdminService service) {
    this.service = service;
  }

  @GetMapping("/topics")
  @PreAuthorize("@authz.can('course.edit')")
  List<TopicView> topics() {
    return service.topics();
  }

  @GetMapping("/courses")
  @PreAuthorize("@authz.can('course.edit')")
  List<CourseListItem> courses() {
    return service.courses();
  }

  @PostMapping("/courses")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('course.edit')")
  CourseView create(Authentication actor, @Valid @RequestBody CourseCreate request) {
    return service.createCourse(actor, request);
  }

  @GetMapping("/courses/{courseId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView course(@PathVariable UUID courseId) {
    return service.course(courseId);
  }

  @PatchMapping("/courses/{courseId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView patch(@PathVariable UUID courseId, @Valid @RequestBody CoursePatch request) {
    return service.patchCourse(courseId, request);
  }

  @PostMapping("/courses/{courseId}/publish")
  @PreAuthorize("@authz.can('course.publish')")
  CourseView publish(Authentication actor, @PathVariable UUID courseId) {
    return service.setCourseStatus(actor, courseId, "published");
  }

  @PostMapping("/courses/{courseId}/unpublish")
  @PreAuthorize("@authz.can('course.publish')")
  CourseView unpublish(Authentication actor, @PathVariable UUID courseId) {
    return service.setCourseStatus(actor, courseId, "draft");
  }

  @PostMapping("/courses/{courseId}/archive")
  @PreAuthorize("@authz.can('course.publish')")
  CourseView archive(Authentication actor, @PathVariable UUID courseId) {
    return service.setCourseStatus(actor, courseId, "archived");
  }

  // ---------------------------------------------------------------- sections

  @PostMapping("/courses/{courseId}/sections")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView addSection(@PathVariable UUID courseId, @Valid @RequestBody SectionCreate request) {
    return service.createSection(courseId, request.title());
  }

  @PutMapping("/courses/{courseId}/sections/order")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView reorderSections(
      @PathVariable UUID courseId, @Valid @RequestBody OrderRequest request) {
    return service.reorderSections(courseId, request.ids());
  }

  @PatchMapping("/courses/{courseId}/sections/{sectionId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView renameSection(
      @PathVariable UUID courseId,
      @PathVariable UUID sectionId,
      @Valid @RequestBody SectionPatch request) {
    return service.renameSection(courseId, sectionId, request.title());
  }

  @DeleteMapping("/courses/{courseId}/sections/{sectionId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView deleteSection(
      Authentication actor, @PathVariable UUID courseId, @PathVariable UUID sectionId) {
    return service.deleteSection(actor, courseId, sectionId);
  }

  @PutMapping("/courses/{courseId}/sections/{sectionId}/lessons/order")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  CourseView reorderLessons(
      @PathVariable UUID courseId,
      @PathVariable UUID sectionId,
      @Valid @RequestBody OrderRequest request) {
    return service.reorderLessons(courseId, sectionId, request.ids());
  }

  // ---------------------------------------------------------------- lessons

  @PostMapping("/courses/{courseId}/lessons")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  LessonView addLesson(@PathVariable UUID courseId, @Valid @RequestBody LessonCreate request) {
    return service.createLesson(courseId, request);
  }

  @GetMapping("/courses/{courseId}/lessons/{lessonId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  LessonView lesson(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
    return service.lesson(courseId, lessonId);
  }

  @PatchMapping("/courses/{courseId}/lessons/{lessonId}")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  LessonView patchLesson(
      @PathVariable UUID courseId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody LessonPatch request) {
    return service.patchLesson(courseId, lessonId, request);
  }

  @PutMapping("/courses/{courseId}/lessons/{lessonId}/blocks")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  LessonView blocks(
      @PathVariable UUID courseId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody BlocksRequest request) {
    return service.replaceBlocks(courseId, lessonId, request.blocks(), request.expectedVersion());
  }

  @PutMapping("/courses/{courseId}/lessons/{lessonId}/source")
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  LessonView source(
      @PathVariable UUID courseId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody SourceRequest request) {
    return service.setSource(courseId, lessonId, request);
  }

  @PostMapping("/courses/{courseId}/lessons/{lessonId}/publish")
  @PreAuthorize("@authz.can('course.publish')")
  LessonView publishLesson(
      Authentication actor,
      @PathVariable UUID courseId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody PublishRequest request) {
    return service.publishLesson(actor, courseId, lessonId, request.originalityConfirmed());
  }

  @PostMapping("/courses/{courseId}/lessons/{lessonId}/unpublish")
  @PreAuthorize("@authz.can('course.publish')")
  LessonView unpublishLesson(
      Authentication actor, @PathVariable UUID courseId, @PathVariable UUID lessonId) {
    return service.unpublishLesson(actor, courseId, lessonId);
  }

  @DeleteMapping("/courses/{courseId}/lessons/{lessonId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@authz.can('course.edit', 'course', #courseId)")
  void deleteLesson(
      Authentication actor, @PathVariable UUID courseId, @PathVariable UUID lessonId) {
    service.deleteLesson(actor, courseId, lessonId);
  }
}
