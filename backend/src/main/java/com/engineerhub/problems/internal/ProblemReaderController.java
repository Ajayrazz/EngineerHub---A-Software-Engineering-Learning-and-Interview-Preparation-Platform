package com.engineerhub.problems.internal;

import com.engineerhub.problems.internal.ProblemReaderDtos.ProblemStates;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicPattern;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblem;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblemItem;
import com.engineerhub.problems.internal.ProblemReaderDtos.PublicProblemPage;
import com.engineerhub.problems.internal.ProblemReaderDtos.RandomPick;
import com.engineerhub.shared.error.ApiException;
import jakarta.annotation.security.PermitAll;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public problem bank (list, detail, patterns, random) and the caller's own bookmarks/state.
 * Browsing needs no login; a premium statement is released only after the server-side entitlement
 * check; per-user data never rides on the public (cacheable) responses.
 */
@RestController
@RequestMapping("/api/v1")
class ProblemReaderController {

  private static final CacheControl PUBLIC_SHORT =
      CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

  private final ProblemReaderService service;

  ProblemReaderController(ProblemReaderService service) {
    this.service = service;
  }

  @GetMapping("/problems")
  @PermitAll
  ResponseEntity<PublicProblemPage> list(
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String pattern,
      @RequestParam(required = false) String tier,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var f = new ProblemReaderService.Filter(difficulty, topic, pattern, tier, q);
    return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(service.list(f, sort, page, size));
  }

  // declared before "/problems/{slug}" so "random" is never read as a slug
  @GetMapping("/problems/random")
  @PermitAll
  ResponseEntity<RandomPick> random(
      Authentication authentication,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String pattern,
      @RequestParam(required = false) String tier,
      @RequestParam(defaultValue = "false") boolean unsolved) {
    var f = new ProblemReaderService.Filter(difficulty, topic, pattern, tier, null);
    String slug =
        service
            .random(authentication, f, unsolved)
            .orElseThrow(() -> ApiException.notFound("No problem matches these filters."));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RandomPick(slug));
  }

  @GetMapping("/problems/{slug}")
  @PermitAll
  ResponseEntity<PublicProblem> detail(Authentication authentication, @PathVariable String slug) {
    var r = service.detail(authentication, slug);
    return ResponseEntity.ok()
        .cacheControl(r.sameForEveryone() ? PUBLIC_SHORT : CacheControl.noStore())
        .body(r.problem());
  }

  @GetMapping("/patterns")
  @PermitAll
  ResponseEntity<List<PublicPattern>> patterns() {
    return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(service.patterns());
  }

  @PutMapping("/problems/{slug}/bookmark")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  void bookmark(Authentication authentication, @PathVariable String slug) {
    service.bookmark(authentication, slug);
  }

  @DeleteMapping("/problems/{slug}/bookmark")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  void unbookmark(Authentication authentication, @PathVariable String slug) {
    service.unbookmark(authentication, slug);
  }

  @GetMapping("/me/bookmarks")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<List<PublicProblemItem>> bookmarks(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.bookmarks(authentication));
  }

  @GetMapping("/me/problems/states")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<ProblemStates> states(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.states(authentication));
  }
}
