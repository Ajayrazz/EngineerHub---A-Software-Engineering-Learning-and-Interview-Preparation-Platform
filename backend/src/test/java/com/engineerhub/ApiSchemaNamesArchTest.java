package com.engineerhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The OpenAPI schema of a type is named after its SIMPLE class name, so two DTOs with the same
 * simple name in different modules silently overwrite each other in the published contract (the
 * generated client then types one of them as the other). Every API type nested in a {@code *Dtos}
 * holder must therefore have a name that no other one has.
 */
class ApiSchemaNamesArchTest {

  @Test
  void noTwoApiTypesShareASimpleName() {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.engineerhub");
    Map<String, List<String>> bySimpleName = new HashMap<>();
    for (JavaClass c : production) {
      if (c.getEnclosingClass().isPresent()
          && c.getEnclosingClass().get().getSimpleName().endsWith("Dtos")) {
        bySimpleName.computeIfAbsent(c.getSimpleName(), k -> new ArrayList<>()).add(c.getName());
      }
    }
    Map<String, List<String>> clashes = new HashMap<>();
    bySimpleName.forEach(
        (k, v) -> {
          if (v.size() > 1) clashes.put(k, v);
        });
    assertThat(clashes)
        .as("API types sharing a simple name collide in the OpenAPI contract")
        .isEmpty();
    assertThat(bySimpleName).as("the scan really found the DTOs").hasSizeGreaterThan(50);
  }
}
