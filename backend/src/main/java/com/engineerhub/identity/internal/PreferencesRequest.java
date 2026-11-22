package com.engineerhub.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record PreferencesRequest(
    @NotBlank @IanaTimezone String timezone,
    @NotBlank @Pattern(regexp = "^en$", message = "unsupported locale") String locale) {}
