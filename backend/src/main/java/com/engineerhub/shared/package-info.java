/**
 * Shared kernel: cross-cutting building blocks (error model, ids, clock, security utilities,
 * outbox). Declared OPEN so every module may depend on it; it must never depend on a feature
 * module.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.engineerhub.shared;
