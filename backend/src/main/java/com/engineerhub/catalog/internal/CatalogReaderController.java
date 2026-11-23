package com.engineerhub.catalog.internal;

import com.engineerhub.catalog.internal.CatalogDtos.TopicView;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicCourseDetail;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicCoursePage;
import com.engineerhub.catalog.internal.CatalogReaderDtos.PublicLesson;
import jakarta.annotation.security.PermitAll;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public learner-facing catalog. Browsing and free lessons need no login; premium lesson bodies are
 * released only after the server-side entitlement check (403 ENTITLEMENT_REQUIRED otherwise).
 * Responses that are identical for everyone may be cached publicly; anything caller-specific is
 * {@code no-store}.
 */
@RestController
@RequestMapping("/api/v1")
class CatalogReaderController {

  private static final CacheControl PUBLIC_SHORT =
      CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

  private final CatalogReaderService service;

  CatalogReaderController(CatalogReaderService service) {
    this.service = service;
  }

  @GetMapping("/topics")
  @PermitAll
  ResponseEntity<List<TopicView>> topics() {
    return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(service.topics());
  }

  @GetMapping("/courses")
  @PermitAll
  ResponseEntity<PublicCoursePage> courses(
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String tier,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(PUBLIC_SHORT)
        .body(service.courses(topic, difficulty, tier, q, page, size));
  }

  @GetMapping("/courses/{slug}")
  @PermitAll
  ResponseEntity<PublicCourseDetail> course(@PathVariable String slug) {
    return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(service.course(slug));
  }

  @GetMapping("/courses/{slug}/lessons/{lessonSlug}")
  @PermitAll
  ResponseEntity<PublicLesson> lesson(
      Authentication authentication, @PathVariable String slug, @PathVariable String lessonSlug) {
    var result = service.lesson(authentication, slug, lessonSlug);
    return ResponseEntity.ok()
        .cacheControl(result.sameForEveryone() ? PUBLIC_SHORT : CacheControl.noStore())
        .body(result.lesson());
  }
}
