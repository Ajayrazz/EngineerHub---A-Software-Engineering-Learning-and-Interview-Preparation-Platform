package com.engineerhub.problems.internal;

import com.engineerhub.problems.internal.ValidationDtos.ProgramRequest;
import com.engineerhub.problems.internal.ValidationDtos.RunRequest;
import com.engineerhub.problems.internal.ValidationDtos.RunView;
import com.engineerhub.problems.internal.ValidationDtos.ValidationView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authoring side of the validation harness: store the three programs and request a run. Both need
 * {@code problem.edit} on that problem. Nothing here executes code; results arrive from the
 * executor through {@link com.engineerhub.problems.ProblemValidationApi}, which has no HTTP
 * endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/problems/{problemId}/validation")
class ValidationController {

  private final ValidationService service;

  ValidationController(ValidationService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ValidationView view(@PathVariable UUID problemId) {
    return service.view(problemId);
  }

  @PutMapping("/programs/{role}")
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  ValidationView program(
      Authentication actor,
      @PathVariable UUID problemId,
      @PathVariable String role,
      @Valid @RequestBody ProgramRequest request) {
    return service.saveProgram(actor, problemId, role, request.language(), request.source());
  }

  @PostMapping("/runs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@authz.can('problem.edit', 'problem', #problemId)")
  RunView run(
      Authentication actor, @PathVariable UUID problemId, @Valid @RequestBody RunRequest request) {
    return service.requestRun(actor, problemId, request.generatedCases());
  }
}
