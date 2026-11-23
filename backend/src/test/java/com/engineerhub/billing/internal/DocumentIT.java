package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tax configuration and receipts / tax invoices: nothing is assumed (UNCONFIGURED issues nothing),
 * receipts carry no tax wording, invoices are exact snapshots, numbering is gap-free, and a
 * document is only ever available to the owner of the payment.
 */
@IntegrationTest
class DocumentIT {

  private static final String WEBHOOK_SECRET = "it-webhook-secret-not-real";
  private static final String GSTIN = "22AAAAA0000A1Z5";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeRazorpayProvider provider;
  @Autowired DocumentService documents;

  @BeforeEach
  @AfterEach
  void resetGlobalTaxState() {
    provider.reset();
    jdbc.sql(
            "update billing.tax_settings set tax_mode = 'UNCONFIGURED', seller_legal_name = null, seller_address = null, seller_state = null, seller_gstin = null, sac_code = null where id = 1")
        .update();
    jdbc.sql("delete from billing.tax_rules").update();
  }

  private static JwtRequestPostProcessor who(String sub, String... roles) {
    return jwt()
        .jwt(
            j ->
                j.subject(sub)
                    .claim("preferred_username", sub)
                    .claim("email", sub + "@example.test"))
        .authorities(
            Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new));
  }

  private static String sub() {
    return "kc-" + Ids.newId();
  }

  private final JwtRequestPostProcessor admin = who(sub(), "ADMIN");
  private final JwtRequestPostProcessor manager = who(sub(), "CONTENT_MANAGER");

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  // ------------------------------------------------------------------ fixtures

  /** {one-time month price id, recurring month price id} of a fresh premium plan. */
  private String[] catalog() throws Exception {
    String code = "dp-" + Ids.newId().toString().substring(24);
    String plan =
        json(call(
                    post("/api/v1/admin/billing/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Doc %s\"}".formatted(code, code))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(
            put("/api/v1/admin/billing/plans/" + plan + "/entitlements"),
            admin,
            "{\"codes\":[\"premium_content\"]}")
        .andExpect(status().isOk());
    String[] ids = new String[2];
    String[][] specs = {{"ONE_TIME", "29950"}, {"RECURRING", "24900"}};
    for (int n = 0; n < 2; n++) {
      for (JsonNode p :
          json(call(
                      post("/api/v1/admin/billing/plans/" + plan + "/prices"),
                      admin,
                      "{\"billingMode\":\"%s\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":%s}"
                          .formatted((Object[]) specs[n]))
                  .andExpect(status().isCreated()))
              .get("prices")) {
        if (p.get("billingMode").asString().equals(specs[n][0])) {
          ids[n] = p.get("id").asString();
        }
      }
    }
    return ids;
  }

  private ResultActions signed(String body, String eventId) throws Exception {
    return mvc.perform(
        post("/api/v1/webhooks/razorpay")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8))
            .header(
                "X-Razorpay-Signature",
                RazorpaySignatures.hmacHex(WEBHOOK_SECRET, body.getBytes(StandardCharsets.UTF_8)))
            .header("X-Razorpay-Event-Id", eventId));
  }

  /** One-time purchase through the real flow; returns OUR payment id. */
  private UUID buy(JwtRequestPostProcessor u, String priceId) throws Exception {
    String order =
        json(call(
                    post("/api/v1/billing/purchases"),
                    u,
                    "{\"planPriceId\":\"%s\"}".formatted(priceId))
                .andExpect(status().isCreated()))
            .get("orderId")
            .asString();
    String pay = "pay_DOC" + Ids.newId().toString().substring(20).replace("-", "");
    signed(
            "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"%s\",\"amount\":29950,\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"%s\"}}}}"
                .formatted(pay, order),
            "evt_" + pay)
        .andExpect(status().isOk());
    return paymentId(pay);
  }

  /** First charge of a new subscription; returns OUR payment id. */
  private UUID subscribeAndCharge(JwtRequestPostProcessor u, String priceId) throws Exception {
    String ref =
        json(call(
                    post("/api/v1/billing/subscriptions"),
                    u,
                    "{\"planPriceId\":\"%s\"}".formatted(priceId))
                .andExpect(status().isCreated()))
            .get("providerSubscriptionId")
            .asString();
    String pay = "pay_DOS" + Ids.newId().toString().substring(20).replace("-", "");
    signed(
            "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"%s\"}},\"payment\":{\"entity\":{\"id\":\"%s\",\"amount\":24900,\"currency\":\"INR\",\"status\":\"captured\"}}}}"
                .formatted(ref, pay),
            "evt_" + pay)
        .andExpect(status().isOk());
    return paymentId(pay);
  }

  private UUID paymentId(String providerRef) {
    return jdbc.sql("select id from billing.payments where provider_payment_ref = :r")
        .param("r", providerRef)
        .query(UUID.class)
        .single();
  }

  private String settings(String mode, boolean seller) {
    return "{\"mode\":\"%s\"%s}"
        .formatted(
            mode,
            seller
                ? ",\"sellerLegalName\":\"Example Seller Pvt Ltd\",\"sellerAddress\":\"1 Example Road, Example City\",\"sellerState\":\"Example State\",\"sellerGstin\":\"%s\",\"sacCode\":\"999999\""
                    .formatted(GSTIN)
                : "");
  }

  private void rule(String name, int bps, LocalDate from, LocalDate to) throws Exception {
    call(
            post("/api/v1/admin/billing/tax/rules"),
            admin,
            "{\"name\":\"%s\",\"rateBps\":%d,\"validFrom\":\"%s\"%s}"
                .formatted(name, bps, from, to == null ? "" : ",\"validTo\":\"" + to + "\""))
        .andExpect(status().isCreated());
  }

  private static LocalDate today() {
    return LocalDate.now(DocumentPdf.IST);
  }

  private ResultActions download(UUID payment, JwtRequestPostProcessor u) throws Exception {
    return call(get("/api/v1/me/payments/" + payment + "/document"), u, null);
  }

  private String pdfText(ResultActions r) throws Exception {
    byte[] bytes = r.andReturn().getResponse().getContentAsByteArray();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  private String docNumber(UUID payment) {
    return jdbc.sql("select doc_number from billing.documents where payment_id = :p")
        .param("p", payment)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  // ------------------------------------------------------------------ configuration

  @Test
  void taxSettingsAreForBillingManagersOnly() throws Exception {
    var learner = who(sub());
    String rule = "/api/v1/admin/billing/tax/rules";
    for (JwtRequestPostProcessor denied : new JwtRequestPostProcessor[] {learner, manager}) {
      call(get("/api/v1/admin/billing/tax"), denied, null).andExpect(status().isForbidden());
      call(put("/api/v1/admin/billing/tax"), denied, settings("DISABLED", false))
          .andExpect(status().isForbidden());
      call(post(rule), denied, "{\"name\":\"x\",\"rateBps\":100,\"validFrom\":\"2026-01-01\"}")
          .andExpect(status().isForbidden());
      call(patch(rule + "/" + UUID.randomUUID()), denied, "{\"active\":false}")
          .andExpect(status().isForbidden());
    }
    call(get("/api/v1/admin/billing/tax"), null, null).andExpect(status().isUnauthorized());
    call(put("/api/v1/admin/billing/tax"), null, settings("DISABLED", false))
        .andExpect(status().isUnauthorized());
    // nothing changed
    assertThat(
            json(call(get("/api/v1/admin/billing/tax"), admin, null).andExpect(status().isOk()))
                .get("mode")
                .asString())
        .isEqualTo("UNCONFIGURED");
  }

  @Test
  void nothingIsAssumedByDefault() throws Exception {
    JsonNode t = json(call(get("/api/v1/admin/billing/tax"), admin, null));
    assertThat(t.get("mode").asString()).isEqualTo("UNCONFIGURED");
    assertThat(t.get("rules")).isEmpty();
    assertThat(t.path("sellerGstin").isMissingNode()).isTrue();
  }

  @Test
  void settingsAreValidatedForShapeAndEnabledNeedsEverything() throws Exception {
    String url = "/api/v1/admin/billing/tax";
    call(put(url), admin, "{\"mode\":\"MAYBE\"}").andExpect(status().isBadRequest());
    call(put(url), admin, "{}").andExpect(status().isBadRequest());
    call(put(url), admin, "{\"mode\":\"DISABLED\",\"sellerGstin\":\"NOT-A-GSTIN\"}")
        .andExpect(status().isBadRequest());
    call(put(url), admin, "{\"mode\":\"DISABLED\",\"sacCode\":\"12\"}")
        .andExpect(status().isBadRequest());
    call(put(url), admin, settings("ENABLED", false))
        .andExpect(status().isBadRequest()); // no seller
    call(put(url), admin, settings("ENABLED", true)).andExpect(status().isBadRequest()); // no rule
    rule("Example tax", 1800, today().minusDays(30), null);
    // a rule now exists, so only the seller details can be what is missing
    call(put(url), admin, settings("ENABLED", false)).andExpect(status().isBadRequest());
    call(
            put(url),
            admin,
            "{\"mode\":\"ENABLED\",\"sellerLegalName\":\"Example Seller Pvt Ltd\",\"sellerAddress\":\"1 Example Road\",\"sellerState\":\"Example State\",\"sellerGstin\":\"%s\"}"
                .formatted(GSTIN))
        .andExpect(status().isBadRequest()); // no service code
    call(put(url), admin, settings("ENABLED", true))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mode")
                .value("ENABLED"));
    call(put(url), admin, settings("DISABLED", false)).andExpect(status().isOk());
    assertThat(
            jdbc.sql(
                    "select count(*) from admin.audit_logs where action = 'billing.tax.settings.changed'")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void rulesAreValidatedAndCannotOverlapOrLeaveEnabledWithoutOne() throws Exception {
    String rules = "/api/v1/admin/billing/tax/rules";
    LocalDate from = today().minusDays(30);
    call(post(rules), admin, "{\"name\":\"x\",\"rateBps\":0,\"validFrom\":\"%s\"}".formatted(from))
        .andExpect(status().isBadRequest());
    call(
            post(rules),
            admin,
            "{\"name\":\"x\",\"rateBps\":5001,\"validFrom\":\"%s\"}".formatted(from))
        .andExpect(status().isBadRequest());
    call(post(rules), admin, "{\"name\":\"\",\"rateBps\":100,\"validFrom\":\"%s\"}".formatted(from))
        .andExpect(status().isBadRequest());
    call(
            post(rules),
            admin,
            "{\"name\":\"x\",\"rateBps\":100,\"validFrom\":\"%s\",\"validTo\":\"%s\"}"
                .formatted(from, from))
        .andExpect(status().isBadRequest()); // end must be after start
    rule("First", 1800, from, today().plusDays(10));
    call(
            post(rules),
            admin,
            "{\"name\":\"Overlap\",\"rateBps\":500,\"validFrom\":\"%s\"}".formatted(today()))
        .andExpect(status().isConflict());
    rule("Later", 500, today().plusDays(10), null); // touching ranges are fine
    JsonNode t = json(call(get("/api/v1/admin/billing/tax"), admin, null));
    assertThat(t.get("rules")).hasSize(2);

    // ENABLED: the last active rule cannot be switched off
    call(put("/api/v1/admin/billing/tax"), admin, settings("ENABLED", true))
        .andExpect(status().isOk());
    String first = null;
    String later = null;
    for (JsonNode r : t.get("rules")) {
      if (r.get("name").asString().equals("First")) first = r.get("id").asString();
      if (r.get("name").asString().equals("Later")) later = r.get("id").asString();
    }
    call(patch(rules + "/" + later), admin, "{\"active\":false}").andExpect(status().isOk());
    call(patch(rules + "/" + first), admin, "{\"active\":false}").andExpect(status().isConflict());
    // re-activating an overlapping rule is refused too
    call(patch(rules + "/" + later), admin, "{\"active\":true}").andExpect(status().isOk());
    call(patch(rules + "/" + UUID.randomUUID()), admin, "{\"active\":true}")
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ issuing

  @Test
  void whileUnconfiguredNothingIsIssuedButAccessIsStillGranted() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    assertThat(docNumber(pay)).isNull();
    JsonNode b = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(b.get("premium").asBoolean()).isTrue();
    assertThat(b.get("documentsAvailable").asBoolean()).isFalse();
    assertThat(b.get("payments").get(0).path("documentNumber").isMissingNode()).isTrue();
    download(pay, u).andExpect(status().isConflict());
  }

  @Test
  void aReceiptIsIssuedAtCaptureWithNoTaxWording() throws Exception {
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String number = docNumber(pay);
    assertThat(number).startsWith("RCPT/").matches("RCPT/\\d{4}-\\d{2}/\\d{6}");

    JsonNode b = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(b.get("documentsAvailable").asBoolean()).isTrue();
    assertThat(b.get("payments").get(0).get("documentNumber").asString()).isEqualTo(number);

    ResultActions r =
        download(pay, u)
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", Matchers.startsWith("application/pdf")))
            .andExpect(
                header().string("Content-Disposition", Matchers.containsString("attachment")))
            .andExpect(header().string("Content-Disposition", Matchers.containsString(".pdf")))
            .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    byte[] bytes = r.andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    String text = pdfText(r);
    assertThat(text).contains(number, "Amount paid: INR 299.50", "TEST MODE");
    assertThat(text.toLowerCase()).doesNotContain("tax").doesNotContain("gst");

    // downloading again returns the same document and consumes no new number
    long before = sequence("receipt");
    download(pay, u).andExpect(status().isOk());
    assertThat(sequence("receipt")).isEqualTo(before);
  }

  @Test
  void aTaxInvoiceIsAnExactSnapshotWithTheSplitAndIgnoresLaterConfiguration() throws Exception {
    rule("Example tax", 1800, today().minusDays(30), null);
    call(put("/api/v1/admin/billing/tax"), admin, settings("ENABLED", true))
        .andExpect(status().isOk());
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    assertThat(docNumber(pay)).matches("INV/\\d{4}-\\d{2}/\\d{6}");

    var row =
        jdbc.sql(
                "select total_minor, net_minor, tax_minor, tax_rate_bps, tax_rule_name, seller_gstin, seller_legal_name, doc_type from billing.documents where payment_id = :p")
            .param("p", pay)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getLong(1),
                      rs.getLong(2),
                      rs.getLong(3),
                      rs.getInt(4),
                      rs.getString(5),
                      rs.getString(6),
                      rs.getString(7),
                      rs.getString(8)
                    })
            .single();
    assertThat(row[0]).isEqualTo(29950L);
    assertThat((Long) row[1] + (Long) row[2]).isEqualTo(29950L);
    assertThat(row[2]).isEqualTo(TaxMath.extractFromInclusive(29950, 1800).taxMinor());
    assertThat(row[3]).isEqualTo(1800);
    assertThat(row[4]).isEqualTo("Example tax");
    assertThat(row[5]).isEqualTo(GSTIN);
    assertThat(row[7]).isEqualTo("tax_invoice");

    String before = pdfText(download(pay, u).andExpect(status().isOk()));
    assertThat(before)
        .contains(
            "TAX INVOICE",
            "Taxable value: INR",
            "Example tax @ 18.00%",
            "GSTIN: " + GSTIN,
            "Total: INR 299.50");

    // later changes to the configuration never alter an issued document
    call(
            put("/api/v1/admin/billing/tax"),
            admin,
            "{\"mode\":\"ENABLED\",\"sellerLegalName\":\"Someone Else\",\"sellerAddress\":\"Elsewhere\",\"sellerState\":\"Other\",\"sellerGstin\":\"%s\",\"sacCode\":\"888888\"}"
                .formatted(GSTIN))
        .andExpect(status().isOk());
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    assertThat(pdfText(download(pay, u).andExpect(status().isOk()))).isEqualTo(before);
  }

  @Test
  void aSubscriptionChargeGetsItsOwnDocument() throws Exception {
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    String[] c = catalog();
    var u = who(sub());
    UUID pay = subscribeAndCharge(u, c[1]);
    assertThat(docNumber(pay)).startsWith("RCPT/");
    String text = pdfText(download(pay, u).andExpect(status().isOk()));
    assertThat(text).contains("auto-renewing subscription", "Amount paid: INR 249.00");
  }

  @Test
  void aPaymentCapturedBeforeConfigurationGetsItsDocumentOnFirstDownload() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    assertThat(docNumber(pay)).isNull();
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    download(pay, u).andExpect(status().isOk());
    assertThat(docNumber(pay)).startsWith("RCPT/");
    var issuedAfterPayment =
        jdbc.sql("select issued_at >= paid_at from billing.documents where payment_id = :p")
            .param("p", pay)
            .query(Boolean.class)
            .single();
    assertThat(issuedAfterPayment).isTrue();
  }

  @Test
  void enabledWithoutAnApplicableRuleIssuesNothingYetNeverBlocksAccess() throws Exception {
    rule("Future rule", 1800, today().plusDays(10), null); // does not cover today
    call(put("/api/v1/admin/billing/tax"), admin, settings("ENABLED", true))
        .andExpect(status().isOk());
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    assertThat(docNumber(pay)).isNull();
    assertThat(json(call(get("/api/v1/me/access"), u, null)).get("premium").asBoolean()).isTrue();
    download(pay, u).andExpect(status().isConflict());
  }

  // ------------------------------------------------------------------ ownership

  @Test
  void aDocumentIsOnlyAvailableToTheOwnerOfThePayment() throws Exception {
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    String[] c = catalog();
    var owner = who(sub());
    UUID pay = buy(owner, c[0]);
    download(pay, owner).andExpect(status().isOk());
    download(pay, who(sub())).andExpect(status().isNotFound());
    download(pay, admin).andExpect(status().isNotFound()); // even staff have no route to it
    download(pay, null).andExpect(status().isUnauthorized());
    download(UUID.randomUUID(), owner).andExpect(status().isNotFound());
    call(get("/api/v1/me/payments/not-a-uuid/document"), owner, null)
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ numbering and immutability

  private long sequence(String type) {
    return jdbc.sql(
            "select coalesce(max(last_no), 0) from billing.document_sequences where doc_type = :t")
        .param("t", type)
        .query(Long.class)
        .single();
  }

  @Test
  void numbersAreConsecutiveWithoutGaps() throws Exception {
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    String[] c = catalog();
    List<Integer> seqs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      UUID pay = buy(who(sub()), c[0]);
      String n = docNumber(pay);
      seqs.add(Integer.parseInt(n.substring(n.lastIndexOf('/') + 1)));
    }
    assertThat(seqs.get(1)).isEqualTo(seqs.get(0) + 1);
    assertThat(seqs.get(2)).isEqualTo(seqs.get(1) + 1);
  }

  @Test
  void concurrentIssuesOfOnePaymentCreateOneDocumentAndConsumeOneNumber() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]); // captured while UNCONFIGURED: no document yet
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    long before = sequence("receipt");
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<UUID>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      results.add(
          pool.submit(
              () -> {
                go.await();
                return documents.issueIfConfigured(pay).orElseThrow();
              }));
    }
    go.countDown();
    java.util.Set<UUID> ids = new java.util.HashSet<>();
    for (Future<UUID> f : results) ids.add(f.get());
    pool.shutdown();
    assertThat(ids).hasSize(1);
    assertThat(sequence("receipt")).isEqualTo(before + 1);
    assertThat(
            jdbc.sql("select count(*) from billing.documents where payment_id = :p")
                .param("p", pay)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void issuedDocumentsCannotBeChangedOrDeletedEvenWithDirectSql() throws Exception {
    call(put("/api/v1/admin/billing/tax"), admin, settings("DISABLED", false))
        .andExpect(status().isOk());
    String[] c = catalog();
    UUID pay = buy(who(sub()), c[0]);
    assertThatThrownBy(
            () ->
                jdbc.sql("update billing.documents set total_minor = 1 where payment_id = :p")
                    .param("p", pay)
                    .update())
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () ->
                jdbc.sql("delete from billing.documents where payment_id = :p")
                    .param("p", pay)
                    .update())
        .hasMessageContaining("immutable");
  }
}
