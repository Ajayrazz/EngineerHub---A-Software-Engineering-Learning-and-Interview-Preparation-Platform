package com.engineerhub;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

  /** Fails the build if module boundaries are violated (cyclic deps, access to internals). */
  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(EngineerHubApplication.class).verify();
  }
}
