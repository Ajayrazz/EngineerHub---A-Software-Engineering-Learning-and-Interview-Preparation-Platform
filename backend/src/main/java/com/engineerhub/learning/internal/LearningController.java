package com.engineerhub.learning.internal;

import com.engineerhub.learning.internal.LearningDtos.CourseProgress;
import com.engineerhub.learning.internal.LearningDtos.EnrollmentView;
import com.engineerhub.learning.internal.LearningDtos.ProgressUpdate;
import com.engineerhub.learning.internal.LearningDtos.QuizAttemptRequest;
import com.engineerhub.learning.internal.LearningDtos.QuizAttemptResult;
import com.engineerhub.learning.internal.LearningDtos.QuizAttemptSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own enrollments and progress. No endpoint accepts a user id: ownership is the token.
 */
@RestController
@RequestMapping("/api/v1")
class LearningController {

  private final LearningService service;

  LearningController(LearningService service) {
    this.service = service;
  }

  @PostMapping("/courses/{courseId}/enroll")
  @PreAuthorize("isAuthenticated()")
  CourseProgress enroll(Authentication auth, @PathVariable UUID courseId) {
    return service.enroll(auth, courseId);
  }

  @GetMapping("/me/enrollments")
  @PreAuthorize("isAuthenticated()")
  List<EnrollmentView> enrollments(Authentication auth) {
    return service.enrollments(auth);
  }

  @GetMapping("/me/courses/{courseId}/progress")
  @PreAuthorize("isAuthenticated()")
  CourseProgress progress(Authentication auth, @PathVariable UUID courseId) {
    return service.progress(auth, courseId);
  }

  @PutMapping("/lessons/{lessonId}/progress")
  @PreAuthorize("isAuthenticated()")
  CourseProgress update(
      Authentication auth,
      @PathVariable UUID lessonId,
      @Valid @RequestBody ProgressUpdate request) {
    return service.update(auth, lessonId, request.status(), request.scrollPct());
  }

  /** Grades an attempt on the published quiz and returns the key and explanations. */
  @PostMapping("/lessons/{lessonId}/quizzes/{quizId}/attempts")
  @PreAuthorize("isAuthenticated()")
  QuizAttemptResult submitQuiz(
      Authentication auth,
      @PathVariable UUID lessonId,
      @PathVariable UUID quizId,
      @Valid @RequestBody QuizAttemptRequest request) {
    return service.submitQuiz(auth, lessonId, quizId, request.answers());
  }

  @GetMapping("/lessons/{lessonId}/quizzes/{quizId}/attempts")
  @PreAuthorize("isAuthenticated()")
  List<QuizAttemptSummary> quizAttempts(
      Authentication auth, @PathVariable UUID lessonId, @PathVariable UUID quizId) {
    return service.quizAttempts(auth, lessonId, quizId);
  }
}
