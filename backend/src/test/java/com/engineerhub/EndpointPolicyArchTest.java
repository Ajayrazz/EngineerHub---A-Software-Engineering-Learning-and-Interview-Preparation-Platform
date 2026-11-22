package com.engineerhub;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Authorization is enforced by method security, and the URL layer is permissive by design (see
 * SecurityConfig). This test is what makes that safe: <b>every request-handler method in production
 * code must declare an explicit access policy</b> ({@code @PreAuthorize} or {@code @PermitAll}). A
 * new endpoint without one fails the build instead of silently being public.
 */
class EndpointPolicyArchTest {

  private static final ArchRule EVERY_ENDPOINT_DECLARES_A_POLICY =
      methods()
          .that()
          .areMetaAnnotatedWith(RequestMapping.class)
          .should()
          .beAnnotatedWith(PreAuthorize.class)
          .orShould()
          .beAnnotatedWith(PermitAll.class)
          .because(
              "every endpoint needs an explicit authorization decision (docs/09 §8, docs/08 §4)");

  @Test
  void everyProductionEndpointDeclaresAnAccessPolicy() {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.engineerhub");
    assertThat(production.stream().flatMap(c -> c.getMethods().stream()))
        .as("sanity: the importer must actually see the endpoints")
        .anyMatch(m -> m.isMetaAnnotatedWith(RequestMapping.class));
    EVERY_ENDPOINT_DECLARES_A_POLICY.check(production);
  }

  /** Negative control: the rule must fail for an endpoint that has no policy. */
  @Test
  void ruleFailsForAnUnguardedEndpoint() {
    JavaClasses unguarded = new ClassFileImporter().importClasses(Unguarded.class);
    assertThatThrownBy(() -> EVERY_ENDPOINT_DECLARES_A_POLICY.check(unguarded))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("unguarded");
  }

  /** Not a Spring bean (no stereotype), so it is never registered as a real endpoint. */
  static class Unguarded {
    @GetMapping("/oops")
    String unguarded() {
      return "no policy";
    }
  }
}
