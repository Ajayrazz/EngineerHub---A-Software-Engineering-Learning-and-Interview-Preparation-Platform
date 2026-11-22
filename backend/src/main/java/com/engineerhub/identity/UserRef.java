package com.engineerhub.identity;

import java.util.UUID;

/** Minimal, stable view of a user for other modules (never expose the entity). */
public record UserRef(UUID id, String username, boolean active) {}
