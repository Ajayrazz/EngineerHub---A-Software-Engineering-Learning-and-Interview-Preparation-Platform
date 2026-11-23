package com.engineerhub.billing.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class TaxDtos {

  private TaxDtos() {}

  /**
   * All seller fields are optional for DISABLED (a receipt shows them when present) and required
   * for ENABLED.
   */
  record TaxSettingsRequest(
      @NotBlank @Pattern(regexp = "^(UNCONFIGURED|DISABLED|ENABLED)$") String mode,
      @Size(max = 200) String sellerLegalName,
      @Size(max = 500) String sellerAddress,
      @Size(max = 60) String sellerState,
      @Size(max = 15) String sellerGstin,
      @Size(max = 8) String sacCode) {}

  record TaxRuleCreate(
      @NotBlank @Size(max = 60) String name,
      @Min(1) @Max(5000) int rateBps,
      @NotNull LocalDate validFrom,
      LocalDate validTo) {}

  record TaxRulePatch(@NotNull Boolean active) {}

  record TaxRuleView(
      UUID id, String name, int rateBps, LocalDate validFrom, LocalDate validTo, boolean active) {}

  record TaxSettingsView(
      String mode,
      String sellerLegalName,
      String sellerAddress,
      String sellerState,
      String sellerGstin,
      String sacCode,
      Instant updatedAt,
      List<TaxRuleView> rules) {}
}
