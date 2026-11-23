package com.engineerhub.problems.internal;

import com.engineerhub.problems.internal.ProblemDtos.PatternCreate;
import com.engineerhub.problems.internal.ProblemDtos.ProblemCreate;
import com.engineerhub.problems.internal.ProblemDtos.ProblemListItem;
import com.engineerhub.problems.internal.ProblemDtos.ProblemPatch;
import com.engineerhub.problems.internal.ProblemDtos.ProblemView;
import com.engineerhub.problems.internal.ProblemDtos.PublishRequest;
import com.engineerhub.problems.internal.ProblemDtos.SourceRequest;
import com.engineerhub.problems.internal.ProblemDtos.TagView;
import com.engineerhub.problems.internal.ProblemDtos.TagsRequest;
import com.engineerhub.problems.internal.ProblemDtos.TestsRequest;
import com.engineerhub.problems.internal.ProblemDtos.TestsView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * Problem authoring API. Editing a problem needs {@code problem.edit} on that problem (global for
 * admins, scoped for assigned problem setters); creating and listing need the global permission;
 * publishing, unpublishing and archiving need the global, non-grantable {@code problem.publish}.
 * Hidden tests are only ever returned by {@code GET .../tests}, which needs {@code problem.edit} on
 * the problem.
 */
@RestController
@RequestMapping("/api/v1/admin")
class ProblemAdminController {

  private final ProblemAdminService service;

  ProblemAdminController(ProblemAdminService service) {
    this.service = service;
  }

  @GetMapping("/patterns")
  @PreAuthorize("@authz.can('problem.edit')")
  List<TagView> patterns() {
    return service.patterns();
  }

  @PostMapping("/patterns")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('problem.edit')")
  TagView createPattern(@Valid @RequestBody PatternCreate request) {
    return service.createPattern(request);
  }

  @GetMapping("/problems")
  @PreAuthorize("@authz.can('problem.edit')")
  List<ProblemListItem> list() {
    return service.list();
  }

  @PostMapping("/problems")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('problem.edit')")
  ProblemView create(Authentication actor, @Valid @RequestBody ProblemCreate request) {
    return service.create(actor, request);
  }

  @GetMapping("/problems/{problemId}")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ProblemView problem(@PathVariable UUID problemId) {
    return service.problem(problemId);
  }

  @PatchMapping("/problems/{problemId}")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ProblemView patch(@PathVariable UUID problemId, @Valid @RequestBody ProblemPatch request) {
    return service.patch(problemId, request);
  }

  @PutMapping("/problems/{problemId}/tags")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ProblemView tags(@PathVariable UUID problemId, @Valid @RequestBody TagsRequest request) {
    return service.setTags(problemId, request);
  }

  @GetMapping("/problems/{problemId}/tests")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  TestsView tests(@PathVariable UUID problemId) {
    return service.tests(problemId);
  }

  @PutMapping("/problems/{problemId}/tests")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  TestsView replaceTests(
      Authentication actor,
      @PathVariable UUID problemId,
      @Valid @RequestBody TestsRequest request) {
    return service.replaceTests(
        actor, problemId, request.tests(), request.expectedTestcaseVersion());
  }

  @PutMapping("/problems/{problemId}/source")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ProblemView source(@PathVariable UUID problemId, @Valid @RequestBody SourceRequest request) {
    return service.setSource(problemId, request);
  }

  @PostMapping("/problems/{problemId}/publish")
  @PreAuthorize("@authz.can('problem.publish')")
  ProblemView publish(
      Authentication actor,
      @PathVariable UUID problemId,
      @Valid @RequestBody PublishRequest request) {
    return service.publish(actor, problemId, request.originalityConfirmed());
  }

  @PostMapping("/problems/{problemId}/unpublish")
  @PreAuthorize("@authz.can('problem.publish')")
  ProblemView unpublish(Authentication actor, @PathVariable UUID problemId) {
    return service.setStatus(actor, problemId, "draft");
  }

  @PostMapping("/problems/{problemId}/archive")
  @PreAuthorize("@authz.can('problem.publish')")
  ProblemView archive(Authentication actor, @PathVariable UUID problemId) {
    return service.setStatus(actor, problemId, "archived");
  }
}
