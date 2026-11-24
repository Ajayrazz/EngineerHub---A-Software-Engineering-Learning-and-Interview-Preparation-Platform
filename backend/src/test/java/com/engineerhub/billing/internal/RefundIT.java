package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
 * Refunds: the owner's policy and settings, the user's request, admin review with optional
 * four-eyes approval, processing against a provider test double, partial and repeated refunds,
 * access revocation, credit notes, flags and the webhook. No policy is assumed anywhere.
 */
@IntegrationTest
class RefundIT {

  private static final String WEBHOOK_SECRET = "it-webhook-secret-not-real";
  private static final String BASE = "/api/v1/admin/billing";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeRazorpayProvider provider;

  @BeforeEach
  @AfterEach
  void resetGlobalState() {
    provider.reset();
    jdbc.sql(
            "update billing.refund_settings set policy_text = null, request_window_days = null, revoke_access_on_refund = true, approval_threshold_minor = null, auto_detect_duplicates = true where id = 1")
        .update();
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
  private final JwtRequestPostProcessor admin2 = who(sub(), "ADMIN");
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

  /** {one-time month price id (29950), recurring month price id (24900)} */
  private String[] catalog() throws Exception {
    String code = "rf-" + Ids.newId().toString().substring(24);
    String plan =
        json(call(
                    post(BASE + "/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Refund %s\"}".formatted(code, code))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(put(BASE + "/plans/" + plan + "/entitlements"), admin, "{\"codes\":[\"premium_content\"]}")
        .andExpect(status().isOk());
    String[] ids = new String[2];
    String[][] specs = {{"ONE_TIME", "29950"}, {"RECURRING", "24900"}};
    for (int n = 0; n < 2; n++) {
      for (JsonNode p :
          json(call(
                      post(BASE + "/plans/" + plan + "/prices"),
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

  private UUID buy(JwtRequestPostProcessor u, String priceId) throws Exception {
    String order =
        json(call(
                    post("/api/v1/billing/purchases"),
                    u,
                    "{\"planPriceId\":\"%s\"}".formatted(priceId))
                .andExpect(status().isCreated()))
            .get("orderId")
            .asString();
    String pay = "pay_RF" + Ids.newId().toString().substring(20).replace("-", "");
    signed(
            "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"%s\",\"amount\":29950,\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"%s\"}}}}"
                .formatted(pay, order),
            "evt_" + pay)
        .andExpect(status().isOk());
    return jdbc.sql("select id from billing.payments where provider_payment_ref = :r")
        .param("r", pay)
        .query(UUID.class)
        .single();
  }

  private UUID subscribeAndCharge(JwtRequestPostProcessor u, String priceId) throws Exception {
    JsonNode s =
        json(
            call(
                    post("/api/v1/billing/subscriptions"),
                    u,
                    "{\"planPriceId\":\"%s\"}".formatted(priceId))
                .andExpect(status().isCreated()));
    String pay = "pay_RS" + Ids.newId().toString().substring(20).replace("-", "");
    signed(
            "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"%s\"}},\"payment\":{\"entity\":{\"id\":\"%s\",\"amount\":24900,\"currency\":\"INR\",\"status\":\"captured\"}}}}"
                .formatted(s.get("providerSubscriptionId").asString(), pay),
            "evt_" + pay)
        .andExpect(status().isOk());
    return jdbc.sql("select id from billing.payments where provider_payment_ref = :r")
        .param("r", pay)
        .query(UUID.class)
        .single();
  }

  private boolean premium(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/access"), u, null)).get("premium").asBoolean();
  }

  private String request(JwtRequestPostProcessor u, UUID payment) throws Exception {
    return json(call(
                post("/api/v1/billing/refund-requests"),
                u,
                "{\"paymentId\":\"%s\",\"reason\":\"duplicate_payment\",\"details\":\"charged twice\"}"
                    .formatted(payment))
            .andExpect(status().isCreated()))
        .get("id")
        .asString();
  }

  private ResultActions approve(JwtRequestPostProcessor a, String id, Long amount)
      throws Exception {
    return call(
        post(BASE + "/refunds/" + id + "/approve"),
        a,
        amount == null
            ? "{\"note\":\"approved in test\"}"
            : "{\"amountMinor\":%d,\"note\":\"approved in test\"}".formatted(amount));
  }

  private String paymentStatus(UUID payment) {
    return jdbc.sql("select status from billing.payments where id = :p")
        .param("p", payment)
        .query(String.class)
        .single();
  }

  private String requestStatus(String id) {
    return jdbc.sql("select status from billing.refund_requests where id = :i")
        .param("i", UUID.fromString(id))
        .query(String.class)
        .single();
  }

  private void tax(String mode) throws Exception {
    call(put(BASE + "/tax"), admin, "{\"mode\":\"" + mode + "\"}").andExpect(status().isOk());
  }

  private static String pdfText(byte[] bytes) throws Exception {
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  private void settings(String json) throws Exception {
    call(put(BASE + "/refund-settings"), admin, json).andExpect(status().isOk());
  }

  private static String settingsJson(
      String policy, Integer window, boolean revoke, Long threshold, boolean dup) {
    return "{\"policyText\":%s,\"requestWindowDays\":%s,\"revokeAccessOnRefund\":%s,\"approvalThresholdMinor\":%s,\"autoDetectDuplicates\":%s}"
        .formatted(policy == null ? "null" : "\"" + policy + "\"", window, revoke, threshold, dup);
  }

  // ------------------------------------------------------------------ policy and settings

  @Test
  void noPolicyExistsUntilTheOwnerWritesOne() throws Exception {
    JsonNode p =
        json(call(get("/api/v1/billing/refund-policy"), null, null).andExpect(status().isOk()));
    assertThat(p.path("policyText").isMissingNode()).isTrue();
    assertThat(p.path("requestWindowDays").isMissingNode()).isTrue();
    settings(settingsJson("Written by the owner.", 14, true, null, true));
    JsonNode q = json(call(get("/api/v1/billing/refund-policy"), null, null));
    assertThat(q.get("policyText").asString()).isEqualTo("Written by the owner.");
    assertThat(q.get("requestWindowDays").asInt()).isEqualTo(14);
  }

  @Test
  void everyAdminEndpointNeedsBillingManage() throws Exception {
    var learner = who(sub());
    UUID any = UUID.randomUUID();
    for (JwtRequestPostProcessor denied : new JwtRequestPostProcessor[] {null, learner, manager}) {
      int expected = denied == null ? 401 : 403;
      call(get(BASE + "/refund-settings"), denied, null).andExpect(status().is(expected));
      call(put(BASE + "/refund-settings"), denied, settingsJson(null, null, true, null, true))
          .andExpect(status().is(expected));
      call(get(BASE + "/refunds"), denied, null).andExpect(status().is(expected));
      call(get(BASE + "/refund-flags"), denied, null).andExpect(status().is(expected));
      call(get(BASE + "/refunds/" + any), denied, null).andExpect(status().is(expected));
      call(post(BASE + "/refunds/" + any + "/review"), denied, null)
          .andExpect(status().is(expected));
      call(post(BASE + "/refunds/" + any + "/approve"), denied, "{\"note\":\"long enough\"}")
          .andExpect(status().is(expected));
      call(post(BASE + "/refunds/" + any + "/reject"), denied, "{\"note\":\"long enough\"}")
          .andExpect(status().is(expected));
      call(
              post(BASE + "/refunds/" + any + "/retry"),
              denied,
              "{\"confirmNotRefundedAtProvider\":true}")
          .andExpect(status().is(expected));
    }
    call(put(BASE + "/refund-settings"), admin, settingsJson(null, 0, true, null, true))
        .andExpect(status().isBadRequest());
    call(put(BASE + "/refund-settings"), admin, settingsJson(null, null, true, 50L, true))
        .andExpect(status().isBadRequest());
    call(put(BASE + "/refund-settings"), admin, "{}").andExpect(status().isBadRequest());
    call(get(BASE + "/refunds/" + any), admin, null).andExpect(status().isNotFound());
    call(get(BASE + "/refunds?status=bogus"), admin, null).andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ the user's request

  @Test
  void aRequestNeedsLoginOwnershipAndAValidReason() throws Exception {
    String[] c = catalog();
    var owner = who(sub());
    UUID pay = buy(owner, c[0]);
    String body = "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(pay);
    call(post("/api/v1/billing/refund-requests"), null, body).andExpect(status().isUnauthorized());
    call(post("/api/v1/billing/refund-requests"), who(sub()), body)
        .andExpect(status().isNotFound());
    call(
            post("/api/v1/billing/refund-requests"),
            owner,
            "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    call(
            post("/api/v1/billing/refund-requests"),
            owner,
            "{\"paymentId\":\"%s\",\"reason\":\"because\"}".formatted(pay))
        .andExpect(status().isBadRequest());
    call(post("/api/v1/billing/refund-requests"), owner, "{\"reason\":\"other\"}")
        .andExpect(status().isBadRequest());
    call(post("/api/v1/billing/refund-requests"), owner, body).andExpect(status().isCreated());
    call(post("/api/v1/billing/refund-requests"), owner, body)
        .andExpect(status().isConflict()); // one open request
    assertThat(json(call(get("/api/v1/me/refund-requests"), owner, null))).hasSize(1);
    assertThat(json(call(get("/api/v1/me/refund-requests"), who(sub()), null))).isEmpty();
    call(get("/api/v1/me/refund-requests"), null, null).andExpect(status().isUnauthorized());
  }

  @Test
  void theRequestWindowIsTheOwnersAndIsEnforced() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    jdbc.sql("update billing.payments set captured_at = :t where id = :p")
        .param("t", Timestamp.from(Instant.now().minus(Duration.ofDays(3))))
        .param("p", pay)
        .update();
    String body = "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(pay);
    call(post("/api/v1/billing/refund-requests"), u, body)
        .andExpect(status().isCreated()); // no window set: always possible
    call(
            post(
                BASE
                    + "/refunds/"
                    + jdbc.sql("select id from billing.refund_requests where payment_id = :p")
                        .param("p", pay)
                        .query(UUID.class)
                        .single()
                    + "/reject"),
            admin,
            "{\"note\":\"closing for the next test\"}")
        .andExpect(status().isOk());
    settings(settingsJson(null, 2, true, null, true));
    call(post("/api/v1/billing/refund-requests"), u, body)
        .andExpect(status().isConflict()); // 3 days > 2 day window
    JsonNode mine = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(mine.get("payments").get(0).get("refundRequestable").asBoolean()).isFalse();
  }

  // ------------------------------------------------------------------ the workflow

  @Test
  void aFullRefundEndsAccessIssuesACreditNoteAndCannotBeRepeated() throws Exception {
    tax("DISABLED");
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    assertThat(premium(u)).isTrue();
    assertThat(
            json(call(get("/api/v1/me/billing"), u, null))
                .get("payments")
                .get(0)
                .get("refundRequestable")
                .asBoolean())
        .isTrue();

    String id = request(u, pay);
    call(post(BASE + "/refunds/" + id + "/review"), admin, null).andExpect(status().isOk());
    assertThat(requestStatus(id)).isEqualTo("under_review");
    JsonNode done = json(approve(admin, id, null).andExpect(status().isOk()));
    assertThat(done.get("request").get("status").asString()).isEqualTo("processed");

    assertThat(provider.refunds).hasSize(1);
    assertThat(provider.refunds.get(0).amountMinor()).isEqualTo(29950);
    assertThat(provider.refunds.get(0).paymentRef()).startsWith("pay_RF");
    assertThat(provider.refunds.get(0).notes()).containsOnlyKeys("refundId");
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
    assertThat(premium(u)).isFalse(); // the access it bought ended with the refund

    JsonNode mine = json(call(get("/api/v1/me/refund-requests"), u, null)).get(0);
    assertThat(mine.get("status").asString()).isEqualTo("processed");
    assertThat(mine.get("refundedMinor").asLong()).isEqualTo(29950);
    assertThat(mine.get("creditNoteNumber").asString()).matches("CN/\\d{4}-\\d{2}/\\d{6}");
    byte[] pdf =
        call(get("/api/v1/me/refunds/" + mine.get("refundId").asString() + "/credit-note"), u, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String text = pdfText(pdf);
    assertThat(text).contains("CREDIT NOTE", "Amount refunded: INR 299.50", "Against: RCPT/");
    assertThat(text.toLowerCase()).doesNotContain("tax").doesNotContain("gst");

    call(
            post("/api/v1/billing/refund-requests"),
            u,
            "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(pay))
        .andExpect(status().isConflict());
    // a finished refund cannot be rejected afterwards
    call(post(BASE + "/refunds/" + id + "/reject"), admin, "{\"note\":\"too late to reject\"}")
        .andExpect(status().isConflict());
    assertThat(requestStatus(id)).isEqualTo("processed");
    assertThat(
            jdbc.sql(
                    "select count(*) from admin.audit_logs where action in ('billing.refund.approved','billing.refund.review.started')")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void partialRefundsAddUpButNeverExceedWhatWasPaid() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String first = request(u, pay);
    approve(admin, first, 40000L).andExpect(status().isBadRequest()); // more than was paid
    approve(admin, first, 10000L).andExpect(status().isOk());
    assertThat(paymentStatus(pay)).isEqualTo("partially_refunded");
    assertThat(premium(u)).isTrue(); // a partial refund keeps access

    String second = request(u, pay); // asking again is fine once the first is done
    approve(admin, second, 19951L).andExpect(status().isBadRequest()); // only 19950 is left
    approve(admin, second, 19950L).andExpect(status().isOk());
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
    assertThat(premium(u)).isFalse();
    assertThat(provider.refunds).hasSize(2);
    assertThat(provider.refunds.stream().mapToLong(FakeRazorpayProvider.Refund::amountMinor).sum())
        .isEqualTo(29950);
    call(
            post("/api/v1/billing/refund-requests"),
            u,
            "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(pay))
        .andExpect(status().isConflict());
  }

  @Test
  void theOwnerCanKeepAccessAfterAFullRefund() throws Exception {
    settings(settingsJson(null, null, false, null, true));
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    approve(admin, request(u, pay), null).andExpect(status().isOk());
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
    assertThat(premium(u)).isTrue();
  }

  @Test
  void aLargeRefundNeedsASecondApproverWhoIsNotTheReviewer() throws Exception {
    settings(settingsJson(null, null, true, 10000L, true));
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    approve(admin, id, null).andExpect(status().isConflict()); // no review started yet
    call(post(BASE + "/refunds/" + id + "/review"), admin, null).andExpect(status().isOk());
    approve(admin, id, null).andExpect(status().isConflict()); // the reviewer cannot approve alone
    assertThat(provider.refunds).isEmpty();
    approve(admin2, id, 9999L)
        .andExpect(status().isOk()); // below the threshold: no second pair of eyes needed
    String again = request(u, pay);
    call(post(BASE + "/refunds/" + again + "/review"), admin, null).andExpect(status().isOk());
    approve(admin2, again, null).andExpect(status().isOk()); // a different admin approves the rest
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
  }

  @Test
  void rejectingIsFinalExplainedToTheUserAndLeavesTheMoneyAlone() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    call(post(BASE + "/refunds/" + id + "/reject"), admin, "{\"note\":\"x\"}")
        .andExpect(status().isBadRequest());
    call(
            post(BASE + "/refunds/" + id + "/reject"),
            admin,
            "{\"note\":\"Not eligible under the policy.\"}")
        .andExpect(status().isOk());
    JsonNode mine = json(call(get("/api/v1/me/refund-requests"), u, null)).get(0);
    assertThat(mine.get("status").asString()).isEqualTo("rejected");
    assertThat(mine.get("decisionNote").asString()).isEqualTo("Not eligible under the policy.");
    approve(admin, id, null).andExpect(status().isConflict());
    call(post(BASE + "/refunds/" + id + "/reject"), admin, "{\"note\":\"Rejecting again\"}")
        .andExpect(status().isConflict());
    call(post(BASE + "/refunds/" + id + "/review"), admin, null).andExpect(status().isConflict());
    assertThat(provider.refunds).isEmpty();
    assertThat(paymentStatus(pay)).isEqualTo("captured");
    assertThat(premium(u)).isTrue();
    request(u, pay); // the user may ask again after a rejection
  }

  @Test
  void aProviderFailureChangesNothingAndARetryNeedsAnExplicitCheck() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    provider.failNextRefund = true;
    approve(admin, id, null).andExpect(status().isServiceUnavailable());
    assertThat(requestStatus(id)).isEqualTo("failed");
    assertThat(paymentStatus(pay)).isEqualTo("captured");
    assertThat(premium(u)).isTrue();
    assertThat(
            jdbc.sql(
                    "select count(*) from billing.refunds where request_id = :r and status = 'failed'")
                .param("r", UUID.fromString(id))
                .query(Long.class)
                .single())
        .isEqualTo(1);
    call(
            post("/api/v1/billing/refund-requests"),
            u,
            "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(pay))
        .andExpect(status().isConflict()); // still one open request
    approve(admin, id, null).andExpect(status().isConflict()); // plain approve is not a retry

    call(
            post(BASE + "/refunds/" + id + "/retry"),
            admin,
            "{\"confirmNotRefundedAtProvider\":false}")
        .andExpect(status().isBadRequest());
    assertThat(provider.refunds).isEmpty();
    call(post(BASE + "/refunds/" + id + "/retry"), admin, "{\"confirmNotRefundedAtProvider\":true}")
        .andExpect(status().isOk());
    assertThat(provider.refunds).hasSize(1);
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
    call(post(BASE + "/refunds/" + id + "/retry"), admin, "{\"confirmNotRefundedAtProvider\":true}")
        .andExpect(status().isConflict());
  }

  @Test
  void aPendingRefundIsFinishedOnceByTheSignedWebhook() throws Exception {
    tax("DISABLED");
    provider.refundStatus = "pending";
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    approve(admin, id, null).andExpect(status().isOk());
    assertThat(requestStatus(id)).isEqualTo("processing");
    // a refund that is in flight can be neither rejected nor approved again
    call(post(BASE + "/refunds/" + id + "/reject"), admin, "{\"note\":\"too late to reject\"}")
        .andExpect(status().isConflict());
    approve(admin, id, null).andExpect(status().isConflict());
    assertThat(requestStatus(id)).isEqualTo("processing");
    assertThat(paymentStatus(pay))
        .isEqualTo("captured"); // nothing is final until the provider says so
    assertThat(premium(u)).isTrue();
    String ref = provider.refunds.get(0).refundId();
    String body =
        "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"%s\",\"payment_id\":\"x\",\"amount\":29950,\"status\":\"processed\"}}}}"
            .formatted(ref);

    webhook(body, "bad-signature", "evt_rf_forged").andExpect(status().isUnauthorized());
    assertThat(requestStatus(id)).isEqualTo("processing");
    assertThat(json(signed(body, "evt_rf_1").andExpect(status().isOk())).get("status").asString())
        .isEqualTo("applied");
    assertThat(requestStatus(id)).isEqualTo("processed");
    assertThat(paymentStatus(pay)).isEqualTo("refunded");
    assertThat(premium(u)).isFalse();
    // a redelivery under another event id changes nothing and issues no second credit note
    assertThat(json(signed(body, "evt_rf_2").andExpect(status().isOk())).get("status").asString())
        .isEqualTo("ignored_no_transition");
    assertThat(
            jdbc.sql(
                    "select count(*) from billing.credit_notes cn join billing.refunds r on r.id = cn.refund_id where r.payment_id = :p")
                .param("p", pay)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  private ResultActions webhook(String body, String signature, String eventId) throws Exception {
    return mvc.perform(
        post("/api/v1/webhooks/razorpay")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8))
            .header("X-Razorpay-Signature", signature)
            .header("X-Razorpay-Event-Id", eventId));
  }

  @Test
  void aFailedRefundEventAndUnknownRefundsAreHandledSafely() throws Exception {
    provider.refundStatus = "pending";
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    approve(admin, id, null).andExpect(status().isOk());
    String ref = provider.refunds.get(0).refundId();
    String failed =
        "{\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"%s\"}}}}"
            .formatted(ref);
    assertThat(
            json(signed(failed, "evt_rf_f1").andExpect(status().isOk())).get("status").asString())
        .isEqualTo("applied");
    assertThat(requestStatus(id)).isEqualTo("failed");
    assertThat(paymentStatus(pay)).isEqualTo("captured");
    assertThat(premium(u)).isTrue();
    String unknown =
        "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_nobody\"}}}}";
    assertThat(
            json(signed(unknown, "evt_rf_u1").andExpect(status().isOk())).get("status").asString())
        .isEqualTo("unknown_refund");
    String malformed = "{\"event\":\"refund.processed\",\"payload\":{}}";
    assertThat(
            json(signed(malformed, "evt_rf_m1").andExpect(status().isOk()))
                .get("status")
                .asString())
        .isEqualTo("ignored_malformed");
  }

  @Test
  void twoAdminsApprovingAtOnceRefundOnlyOnce() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (JwtRequestPostProcessor a : List.of(admin, admin2)) {
      results.add(
          pool.submit(
              () -> {
                go.await();
                return approve(a, id, null).andReturn().getResponse().getStatus();
              }));
    }
    go.countDown();
    List<Integer> codes = new ArrayList<>();
    for (Future<Integer> f : results) codes.add(f.get());
    pool.shutdown();
    assertThat(codes).containsExactlyInAnyOrder(200, 409);
    assertThat(provider.refunds).hasSize(1);
    assertThat(
            jdbc.sql("select count(*) from billing.refunds where payment_id = :p")
                .param("p", pay)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  // ------------------------------------------------------------------ credit notes

  @Test
  void aCreditNoteExistsOnlyWhenThePaymentHadADocumentAndOnlyItsOwnerSeesIt() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]); // tax is UNCONFIGURED: no receipt was issued
    approve(admin, request(u, pay), null).andExpect(status().isOk());
    JsonNode mine = json(call(get("/api/v1/me/refund-requests"), u, null)).get(0);
    assertThat(mine.path("creditNoteNumber").isMissingNode()).isTrue();
    String refundId = mine.get("refundId").asString();
    call(get("/api/v1/me/refunds/" + refundId + "/credit-note"), u, null)
        .andExpect(status().isNotFound());

    tax("DISABLED");
    var v = who(sub());
    UUID pay2 = buy(v, c[0]);
    approve(admin, request(v, pay2), null).andExpect(status().isOk());
    String ref2 =
        json(call(get("/api/v1/me/refund-requests"), v, null)).get(0).get("refundId").asString();
    call(get("/api/v1/me/refunds/" + ref2 + "/credit-note"), v, null).andExpect(status().isOk());
    call(get("/api/v1/me/refunds/" + ref2 + "/credit-note"), u, null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/me/refunds/" + ref2 + "/credit-note"), admin, null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/me/refunds/" + ref2 + "/credit-note"), null, null)
        .andExpect(status().isUnauthorized());
    call(get("/api/v1/me/refunds/" + UUID.randomUUID() + "/credit-note"), v, null)
        .andExpect(status().isNotFound());
  }

  @Test
  void aCreditNoteAgainstATaxInvoiceSplitsTheRefundByTheOriginalRate() throws Exception {
    call(
            post(BASE + "/tax/rules"),
            admin,
            "{\"name\":\"Example tax\",\"rateBps\":1800,\"validFrom\":\"%s\"}"
                .formatted(LocalDate.now().minusDays(30)))
        .andExpect(status().isCreated());
    call(
            put(BASE + "/tax"),
            admin,
            "{\"mode\":\"ENABLED\",\"sellerLegalName\":\"Example Seller\",\"sellerAddress\":\"1 Example Road\",\"sellerState\":\"Example State\",\"sellerGstin\":\"22AAAAA0000A1Z5\",\"sacCode\":\"999999\"}")
        .andExpect(status().isOk());
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    approve(admin, request(u, pay), 10000L).andExpect(status().isOk());
    var row =
        jdbc.sql(
                "select cn.total_minor, cn.net_minor, cn.tax_minor, cn.tax_rate_bps, cn.original_number from billing.credit_notes cn join billing.refunds r on r.id = cn.refund_id where r.payment_id = :p")
            .param("p", pay)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getString(5)
                    })
            .single();
    var split = TaxMath.extractFromInclusive(10000, 1800);
    assertThat(row[0]).isEqualTo(10000L);
    assertThat(row[1]).isEqualTo(split.netMinor());
    assertThat(row[2]).isEqualTo(split.taxMinor());
    assertThat(row[3]).isEqualTo(1800);
    assertThat((String) row[4]).startsWith("INV/");
    String refundId =
        json(call(get("/api/v1/me/refund-requests"), u, null)).get(0).get("refundId").asString();
    String text =
        pdfText(
            call(get("/api/v1/me/refunds/" + refundId + "/credit-note"), u, null)
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    assertThat(text)
        .contains("Taxable value credited", "Example tax @ 18.00%", "Total credited: INR 100.00");
  }

  @Test
  void creditNotesAreImmutable() throws Exception {
    tax("DISABLED");
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    approve(admin, request(u, pay), null).andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.sql("update billing.credit_notes set total_minor = 1").update())
        .hasMessageContaining("immutable");
  }

  // ------------------------------------------------------------------ independence, queue and
  // flags

  @Test
  void refundingASubscriptionPaymentDoesNotCancelTheSubscription() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = subscribeAndCharge(u, c[1]);
    approve(admin, request(u, pay), null).andExpect(status().isOk());
    assertThat(provider.refunds.get(0).amountMinor()).isEqualTo(24900);
    assertThat(
            jdbc.sql(
                    "select s.status from billing.subscriptions s join billing.payments p on p.subscription_id = s.id where p.id = :p")
                .param("p", pay)
                .query(String.class)
                .single())
        .isEqualTo("active");
    assertThat(provider.cancelledSubscriptions).isEmpty();
  }

  @Test
  void theQueueFiltersByStatusAndShowsTheDetail() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID pay = buy(u, c[0]);
    String id = request(u, pay);
    JsonNode q =
        json(call(get(BASE + "/refunds?status=requested"), admin, null).andExpect(status().isOk()));
    boolean listed = false;
    for (JsonNode r : q) if (r.get("id").asString().equals(id)) listed = true;
    assertThat(listed).isTrue();
    for (JsonNode r : json(call(get(BASE + "/refunds?status=processed"), admin, null)))
      assertThat(r.get("id").asString()).isNotEqualTo(id);
    JsonNode d = json(call(get(BASE + "/refunds/" + id), admin, null).andExpect(status().isOk()));
    assertThat(d.get("request").get("refundableMinor").asLong()).isEqualTo(29950);
    assertThat(d.get("details").asString()).isEqualTo("charged twice");
    assertThat(d.get("paymentStatus").asString()).isEqualTo("captured");
    assertThat(d.get("reviewStarted").asBoolean()).isFalse();
  }

  @Test
  void duplicateCapturesAndMoneyWithoutAccessAreFlaggedNotRefunded() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID first = buy(u, c[0]);
    UUID second = buy(u, c[0]); // same user, same plan, within a day
    var flags = json(call(get(BASE + "/refund-flags"), admin, null).andExpect(status().isOk()));
    List<String> kinds = new ArrayList<>();
    for (JsonNode f : flags)
      if (f.get("paymentId").asString().equals(second.toString())
          || f.get("paymentId").asString().equals(first.toString()))
        kinds.add(f.get("kind").asString());
    assertThat(kinds).containsExactly("duplicate_capture", "duplicate_capture");
    assertThat(paymentStatus(first)).isEqualTo("captured"); // flags never refund by themselves
    assertThat(provider.refunds).isEmpty();

    settings(settingsJson(null, null, true, null, false)); // detection off
    for (JsonNode f : json(call(get(BASE + "/refund-flags"), admin, null)))
      assertThat(f.get("paymentId").asString()).isNotIn(first.toString(), second.toString());

    // money taken, no access, a while ago
    var v = who(sub());
    UUID lonely = buy(v, c[0]);
    jdbc.sql("delete from billing.access_periods where source_ref = :r")
        .param("r", lonely.toString())
        .update();
    jdbc.sql("update billing.payments set captured_at = :t where id = :p")
        .param("t", Timestamp.from(Instant.now().minus(Duration.ofHours(1))))
        .param("p", lonely)
        .update();
    boolean flagged = false;
    for (JsonNode f : json(call(get(BASE + "/refund-flags"), admin, null)))
      if (f.get("paymentId").asString().equals(lonely.toString())
          && f.get("kind").asString().equals("no_access_granted")) flagged = true;
    assertThat(flagged).isTrue();
  }
}
