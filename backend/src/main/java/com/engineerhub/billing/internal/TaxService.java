package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.TaxDtos.TaxRuleCreate;
import com.engineerhub.billing.internal.TaxDtos.TaxRuleView;
import com.engineerhub.billing.internal.TaxDtos.TaxSettingsRequest;
import com.engineerhub.billing.internal.TaxDtos.TaxSettingsView;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tax configuration (docs/21 §5). The owner decides everything here (Pending Decision P-4); the
 * service only validates SHAPE: a GSTIN has the 15-character format (its checksum and whether it is
 * registered are not checked), a rule has a sane rate and date range, and ENABLED needs complete
 * seller details and at least one rule. Changes apply to documents issued afterwards; issued
 * documents are immutable snapshots.
 */
@Service
class TaxService {

  private static final Pattern GSTIN =
      Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
  private static final Pattern SAC = Pattern.compile("^[0-9]{4,8}$");

  private final JdbcClient jdbc;
  private final Clock clock;
  private final AuditLog audit;
  private final UserDirectory directory;

  TaxService(JdbcClient jdbc, Clock clock, AuditLog audit, UserDirectory directory) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.audit = audit;
    this.directory = directory;
  }

  @Transactional(readOnly = true)
  TaxSettingsView get() {
    List<TaxRuleView> rules =
        jdbc.sql(
                "select id, name, rate_bps, valid_from, valid_to, active from billing.tax_rules order by valid_from desc, created_at desc")
            .query(
                (rs, i) ->
                    new TaxRuleView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getInt(3),
                        rs.getDate(4).toLocalDate(),
                        rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(),
                        rs.getBoolean(6)))
            .list();
    return jdbc.sql(
            "select tax_mode, seller_legal_name, seller_address, seller_state, seller_gstin, sac_code, updated_at from billing.tax_settings where id = 1")
        .query(
            (rs, i) ->
                new TaxSettingsView(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getTimestamp(7).toInstant(),
                    rules))
        .single();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }

  @Transactional
  TaxSettingsView update(Authentication actor, TaxSettingsRequest r) {
    String name = blankToNull(r.sellerLegalName());
    String address = blankToNull(r.sellerAddress());
    String state = blankToNull(r.sellerState());
    String gstin = blankToNull(r.sellerGstin());
    String sac = blankToNull(r.sacCode());
    if (gstin != null && !GSTIN.matcher(gstin).matches()) {
      throw invalid("GSTIN must have the 15-character GST format (for example 22AAAAA0000A1Z5).");
    }
    if (sac != null && !SAC.matcher(sac).matches()) {
      throw invalid("The service code (SAC) must be 4 to 8 digits.");
    }
    if (r.mode().equals("ENABLED")) {
      if (name == null || address == null || state == null || gstin == null || sac == null) {
        throw invalid(
            "ENABLED needs the seller's legal name, address, state, GSTIN and service code (SAC).");
      }
      long rules =
          jdbc.sql("select count(*) from billing.tax_rules where active")
              .query(Long.class)
              .single();
      if (rules == 0) {
        throw invalid("ENABLED needs at least one active tax rule.");
      }
    }
    var before = get();
    jdbc.sql(
            "update billing.tax_settings set tax_mode = :m, seller_legal_name = :n, seller_address = :a, seller_state = :s, seller_gstin = :g, sac_code = :c, updated_at = :now, updated_by = :by where id = 1")
        .param("m", r.mode())
        .param("n", name, java.sql.Types.VARCHAR)
        .param("a", address, java.sql.Types.VARCHAR)
        .param("s", state, java.sql.Types.VARCHAR)
        .param("g", gstin, java.sql.Types.VARCHAR)
        .param("c", sac, java.sql.Types.VARCHAR)
        .param("now", Timestamp.from(clock.instant()))
        .param("by", directory.current(actor).id())
        .update();
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.tax.settings.changed", "tax_settings", "1")
            .withChange(Map.of("mode", before.mode()), Map.of("mode", r.mode())));
    return get();
  }

  @Transactional
  TaxSettingsView addRule(Authentication actor, TaxRuleCreate r) {
    if (r.validTo() != null && !r.validTo().isAfter(r.validFrom())) {
      throw invalid("The end date must be after the start date.");
    }
    if (overlaps(r.validFrom(), r.validTo(), null)) {
      throw ApiException.conflict("An active rule already covers part of this period.");
    }
    UUID id = Ids.newId();
    jdbc.sql(
            "insert into billing.tax_rules (id, name, rate_bps, valid_from, valid_to, created_at) values (:id, :n, :r, :f, :t, :now)")
        .param("id", id)
        .param("n", r.name().strip())
        .param("r", r.rateBps())
        .param("f", java.sql.Date.valueOf(r.validFrom()))
        .param(
            "t",
            r.validTo() == null ? null : java.sql.Date.valueOf(r.validTo()),
            java.sql.Types.DATE)
        .param("now", Timestamp.from(clock.instant()))
        .update();
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.tax.rule.created", "tax_rule", id.toString())
            .withChange(null, Map.of("rateBps", r.rateBps())));
    return get();
  }

  @Transactional
  TaxSettingsView setRuleActive(Authentication actor, UUID ruleId, boolean active) {
    var rule =
        jdbc.sql(
                "select valid_from, valid_to, active from billing.tax_rules where id = :id for update")
            .param("id", ruleId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getDate(1).toLocalDate(),
                      rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate(),
                      rs.getBoolean(3)
                    })
            .optional()
            .orElseThrow(() -> ApiException.notFound("Tax rule not found."));
    if ((Boolean) rule[2] == active) {
      return get();
    }
    if (active && overlaps((LocalDate) rule[0], (LocalDate) rule[1], ruleId)) {
      throw ApiException.conflict("Another active rule covers part of this period.");
    }
    if (!active) {
      String mode =
          jdbc.sql("select tax_mode from billing.tax_settings where id = 1")
              .query(String.class)
              .single();
      long others =
          jdbc.sql("select count(*) from billing.tax_rules where active and id <> :id")
              .param("id", ruleId)
              .query(Long.class)
              .single();
      if (mode.equals("ENABLED") && others == 0) {
        throw ApiException.conflict(
            "Tax is ENABLED: keep at least one active rule or change the mode first.");
      }
    }
    jdbc.sql("update billing.tax_rules set active = :a where id = :id")
        .param("a", active)
        .param("id", ruleId)
        .update();
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.tax.rule.active.changed", "tax_rule", ruleId.toString())
            .withChange(Map.of("active", !active), Map.of("active", active)));
    return get();
  }

  /**
   * Does an ACTIVE rule (other than {@code except}) overlap [from, to)? A null end is open-ended.
   */
  private boolean overlaps(LocalDate from, LocalDate to, UUID except) {
    LocalDate far = LocalDate.of(9999, 12, 31);
    return jdbc.sql(
                "select count(*) from billing.tax_rules where active and (cast(:ex as uuid) is null or id <> :ex) and valid_from < :to and coalesce(valid_to, date '9999-12-31') > :from")
            .param("ex", except, java.sql.Types.OTHER)
            .param("from", java.sql.Date.valueOf(from))
            .param("to", java.sql.Date.valueOf(to == null ? far : to))
            .query(Long.class)
            .single()
        > 0;
  }
}
