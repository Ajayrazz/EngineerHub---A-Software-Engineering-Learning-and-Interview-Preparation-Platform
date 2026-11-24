package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.DocumentPdf.DocumentRow;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.id.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipts and tax invoices (docs/21 §5). A document is issued at most once per payment and is an
 * immutable snapshot. Nothing is issued while the tax mode is UNCONFIGURED. Issue is attempted when
 * a payment is captured and again on the owner's first download, so a payment captured before the
 * owner configured tax can still get its document (its issue date is then later than its payment
 * date, and both are printed).
 *
 * <p>The payment row is locked first, so two concurrent issues of the same payment cannot both
 * consume a number: numbering stays gap-free.
 */
@Service
class DocumentService {

  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final UserDirectory directory;
  private final RazorpayProperties provider;

  DocumentService(
      JdbcClient jdbc, Clock clock, UserDirectory directory, RazorpayProperties provider) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.directory = directory;
    this.provider = provider;
  }

  private record Settings(
      String mode, String legalName, String address, String state, String gstin, String sac) {}

  private Settings settings() {
    return jdbc.sql(
            "select tax_mode, seller_legal_name, seller_address, seller_state, seller_gstin, sac_code from billing.tax_settings where id = 1")
        .query(
            (rs, i) ->
                new Settings(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6)))
        .single();
  }

  /**
   * Issues the document of a payment if the configuration allows it; returns its id if one exists.
   */
  @Transactional
  Optional<UUID> issueIfConfigured(UUID paymentId) {
    // serialise issuing per payment (also guarantees the payment exists)
    var pay =
        jdbc.sql(
                "select user_id, amount_minor, currency, captured_at, intent_id, subscription_id from billing.payments where id = :id for update")
            .param("id", paymentId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getLong(2),
                      rs.getString(3).strip(),
                      rs.getTimestamp(4).toInstant(),
                      rs.getObject(5, UUID.class),
                      rs.getObject(6, UUID.class)
                    })
            .optional();
    if (pay.isEmpty()) {
      return Optional.empty();
    }
    Optional<UUID> existing =
        jdbc.sql("select id from billing.documents where payment_id = :p")
            .param("p", paymentId)
            .query(UUID.class)
            .optional();
    if (existing.isPresent()) {
      return existing;
    }
    Settings s = settings();
    if (s.mode().equals("UNCONFIGURED")) {
      return Optional.empty();
    }
    Object[] p = pay.get();
    UUID userId = (UUID) p[0];
    long total = (Long) p[1];
    String currency = (String) p[2];
    Instant paidAt = (Instant) p[3];
    boolean recurring = p[5] != null;

    var plan =
        jdbc.sql(
                recurring
                    ? "select pl.name, sub.period_unit from billing.subscriptions sub join billing.plans pl on pl.id = sub.plan_id where sub.id = :id"
                    : "select pl.name, i.period_unit from billing.purchase_intents i join billing.plans pl on pl.id = i.plan_id where i.id = :id")
            .param("id", recurring ? p[5] : p[4])
            .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2).strip()})
            .single();

    boolean invoice = s.mode().equals("ENABLED");
    long net = total;
    long tax = 0;
    Integer rateBps = null;
    String ruleName = null;
    if (invoice) {
      LocalDate supplyDate = paidAt.atZone(DocumentPdf.IST).toLocalDate();
      var rule =
          jdbc.sql(
                  "select name, rate_bps from billing.tax_rules where active and valid_from <= :d and (valid_to is null or :d < valid_to) order by valid_from desc limit 1")
              .param("d", java.sql.Date.valueOf(supplyDate))
              .query((rs, i) -> new Object[] {rs.getString(1), rs.getInt(2)})
              .optional();
      if (rule.isEmpty() || !sellerComplete(s)) {
        log.warn(
            "Tax mode is ENABLED but no applicable rule or complete seller details: no document issued");
        return Optional.empty();
      }
      rateBps = (Integer) rule.get()[1];
      ruleName = (String) rule.get()[0];
      TaxMath.Split split = TaxMath.extractFromInclusive(total, rateBps);
      net = split.netMinor();
      tax = split.taxMinor();
    }

    Instant now = clock.instant();
    String fy = TaxMath.financialYear(now.atZone(DocumentPdf.IST).toLocalDate());
    String type = invoice ? "tax_invoice" : "receipt";
    String number = "%s/%s/%06d".formatted(invoice ? "INV" : "RCPT", fy, nextNumber(fy, type));
    String customer = directory.findById(userId).map(u -> u.username()).orElse("EngineerHub user");
    UUID id = Ids.newId();
    jdbc.sql(
            """
            insert into billing.documents (id, payment_id, user_id, doc_type, doc_number, financial_year, issued_at, paid_at, currency,
              total_minor, net_minor, tax_minor, tax_rate_bps, tax_rule_name, plan_name, period_unit, billing_mode, customer_name,
              seller_legal_name, seller_address, seller_state, seller_gstin, sac_code, test_mode)
            values (:id, :pay, :u, :t, :num, :fy, :now, :paid, :cur, :total, :net, :tax, :rate, :rule, :plan, :unit, :mode, :cust,
              :sn, :sa, :ss, :sg, :sac, :test)
            """)
        .param("id", id)
        .param("pay", paymentId)
        .param("u", userId)
        .param("t", type)
        .param("num", number)
        .param("fy", fy)
        .param("now", Timestamp.from(now))
        .param("paid", Timestamp.from(paidAt))
        .param("cur", currency)
        .param("total", total)
        .param("net", net)
        .param("tax", tax)
        .param("rate", rateBps, java.sql.Types.INTEGER)
        .param("rule", ruleName, java.sql.Types.VARCHAR)
        .param("plan", plan[0])
        .param("unit", plan[1])
        .param("mode", recurring ? "RECURRING" : "ONE_TIME")
        .param("cust", customer)
        .param("sn", s.legalName(), java.sql.Types.VARCHAR)
        .param("sa", s.address(), java.sql.Types.VARCHAR)
        .param("ss", invoice ? s.state() : null, java.sql.Types.VARCHAR)
        .param("sg", invoice ? s.gstin() : null, java.sql.Types.VARCHAR)
        .param("sac", invoice ? s.sac() : null, java.sql.Types.VARCHAR)
        .param("test", provider.mode() == null || "test".equals(provider.mode()))
        .update();
    return Optional.of(id);
  }

  /**
   * Next gap-free number of a type in a financial year; call in the same transaction as the insert.
   */
  long nextNumber(String fy, String type) {
    return jdbc.sql(
            "insert into billing.document_sequences (financial_year, doc_type, last_no) values (:fy, :t, 1) on conflict (financial_year, doc_type) do update set last_no = billing.document_sequences.last_no + 1 returning last_no")
        .param("fy", fy)
        .param("t", type)
        .query(Long.class)
        .single();
  }

  /**
   * The credit note of a processed refund: an immutable snapshot against the payment's own
   * document, with the refunded amount split by the ORIGINAL document's rate (tax-inclusive
   * extraction, the same rounding). No document, no credit note. Idempotent per refund.
   */
  @Transactional
  Optional<UUID> issueCreditNote(UUID refundId) {
    var existing =
        jdbc.sql("select id from billing.credit_notes where refund_id = :r")
            .param("r", refundId)
            .query(UUID.class)
            .optional();
    if (existing.isPresent()) {
      return existing;
    }
    var orig =
        jdbc.sql(
                """
                select d.id, d.doc_number, d.user_id, d.currency, d.tax_rate_bps, d.tax_rule_name, d.plan_name, d.period_unit, d.billing_mode,
                       d.customer_name, d.seller_legal_name, d.seller_address, d.seller_state, d.seller_gstin, d.sac_code, d.test_mode, r.amount_minor
                from billing.refunds r join billing.documents d on d.payment_id = r.payment_id
                where r.id = :r
                """)
            .param("r", refundId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getString(2),
                      rs.getObject(3, UUID.class),
                      rs.getString(4).strip(),
                      rs.getObject(5, Integer.class),
                      rs.getString(6),
                      rs.getString(7),
                      rs.getString(8).strip(),
                      rs.getString(9).strip(),
                      rs.getString(10),
                      rs.getString(11),
                      rs.getString(12),
                      rs.getString(13),
                      rs.getString(14),
                      rs.getString(15),
                      rs.getBoolean(16),
                      rs.getLong(17)
                    })
            .optional();
    if (orig.isEmpty()) {
      return Optional.empty();
    }
    Object[] o = orig.get();
    long total = (Long) o[16];
    Integer rate = (Integer) o[4];
    TaxMath.Split split =
        rate == null ? new TaxMath.Split(total, 0) : TaxMath.extractFromInclusive(total, rate);
    Instant now = clock.instant();
    String fy = TaxMath.financialYear(now.atZone(DocumentPdf.IST).toLocalDate());
    String number = "CN/%s/%06d".formatted(fy, nextNumber(fy, "credit_note"));
    UUID id = Ids.newId();
    jdbc.sql(
            """
            insert into billing.credit_notes (id, refund_id, document_id, user_id, doc_number, original_number, financial_year, issued_at, currency,
              total_minor, net_minor, tax_minor, tax_rate_bps, tax_rule_name, plan_name, period_unit, billing_mode, customer_name,
              seller_legal_name, seller_address, seller_state, seller_gstin, sac_code, test_mode)
            values (:id, :ref, :doc, :u, :num, :orig, :fy, :now, :cur, :total, :net, :tax, :rate, :rule, :plan, :unit, :mode, :cust,
              :sn, :sa, :ss, :sg, :sac, :test)
            """)
        .param("id", id)
        .param("ref", refundId)
        .param("doc", o[0])
        .param("u", o[2])
        .param("num", number)
        .param("orig", o[1])
        .param("fy", fy)
        .param("now", Timestamp.from(now))
        .param("cur", o[3])
        .param("total", total)
        .param("net", split.netMinor())
        .param("tax", split.taxMinor())
        .param("rate", rate, java.sql.Types.INTEGER)
        .param("rule", o[5], java.sql.Types.VARCHAR)
        .param("plan", o[6])
        .param("unit", o[7])
        .param("mode", o[8])
        .param("cust", o[9])
        .param("sn", o[10], java.sql.Types.VARCHAR)
        .param("sa", o[11], java.sql.Types.VARCHAR)
        .param("ss", o[12], java.sql.Types.VARCHAR)
        .param("sg", o[13], java.sql.Types.VARCHAR)
        .param("sac", o[14], java.sql.Types.VARCHAR)
        .param("test", o[15])
        .update();
    return Optional.of(id);
  }

  /** The credit note PDF of one of the caller's own refunds. */
  /** Not read-only: resolving the caller may provision the local user. */
  PdfFile creditNotePdf(Authentication auth, UUID refundId) {
    UUID user = directory.current(auth).id();
    DocumentRow row =
        jdbc.sql(
                """
                select doc_number, original_number, issued_at, currency, total_minor, net_minor, tax_minor, tax_rate_bps, tax_rule_name,
                       plan_name, period_unit, billing_mode, customer_name, seller_legal_name, seller_address, seller_state, seller_gstin, sac_code, test_mode,
                       (select p.captured_at from billing.refunds r join billing.payments p on p.id = r.payment_id where r.id = refund_id)
                from billing.credit_notes where refund_id = :r and user_id = :u
                """)
            .param("r", refundId)
            .param("u", user)
            .query(
                (rs, i) ->
                    new DocumentRow(
                        "credit_note",
                        rs.getString(1),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(20).toInstant(),
                        rs.getString(4).strip(),
                        rs.getLong(5),
                        rs.getLong(6),
                        rs.getLong(7),
                        rs.getObject(8, Integer.class),
                        rs.getString(9),
                        rs.getString(10),
                        rs.getString(11).strip(),
                        rs.getString(12).strip(),
                        rs.getString(13),
                        rs.getString(14),
                        rs.getString(15),
                        rs.getString(16),
                        rs.getString(17),
                        rs.getString(18),
                        rs.getBoolean(19),
                        rs.getString(2)))
            .optional()
            .orElseThrow(() -> ApiException.notFound("Credit note not found."));
    return new PdfFile(row.number().replace('/', '-') + ".pdf", DocumentPdf.render(row));
  }

  static boolean sellerComplete(Settings s) {
    return notBlank(s.legalName())
        && notBlank(s.address())
        && notBlank(s.state())
        && notBlank(s.gstin())
        && notBlank(s.sac());
  }

  private static boolean notBlank(String v) {
    return v != null && !v.isBlank();
  }

  /**
   * The PDF of the caller's own payment, issuing the document first if it is allowed and missing.
   */
  @Transactional
  PdfFile pdfFor(Authentication auth, UUID paymentId) {
    UUID user = directory.current(auth).id();
    UUID owner =
        jdbc.sql("select user_id from billing.payments where id = :id")
            .param("id", paymentId)
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (owner == null || !owner.equals(user)) {
      throw ApiException.notFound(
          "Payment not found."); // someone else's looks exactly like missing
    }
    UUID docId =
        issueIfConfigured(paymentId)
            .orElseThrow(() -> ApiException.conflict("Receipts are not available yet."));
    DocumentRow row =
        jdbc.sql(
                """
                select doc_type, doc_number, issued_at, paid_at, currency, total_minor, net_minor, tax_minor, tax_rate_bps, tax_rule_name,
                       plan_name, period_unit, billing_mode, customer_name, seller_legal_name, seller_address, seller_state, seller_gstin, sac_code, test_mode
                from billing.documents where id = :id
                """)
            .param("id", docId)
            .query(
                (rs, i) ->
                    new DocumentRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(),
                        rs.getString(5).strip(),
                        rs.getLong(6),
                        rs.getLong(7),
                        rs.getLong(8),
                        rs.getObject(9, Integer.class),
                        rs.getString(10),
                        rs.getString(11),
                        rs.getString(12).strip(),
                        rs.getString(13).strip(),
                        rs.getString(14),
                        rs.getString(15),
                        rs.getString(16),
                        rs.getString(17),
                        rs.getString(18),
                        rs.getString(19),
                        rs.getBoolean(20),
                        null))
            .single();
    String file = row.number().replace('/', '-') + ".pdf";
    return new PdfFile(file, DocumentPdf.render(row));
  }

  record PdfFile(String filename, byte[] bytes) {}
}
