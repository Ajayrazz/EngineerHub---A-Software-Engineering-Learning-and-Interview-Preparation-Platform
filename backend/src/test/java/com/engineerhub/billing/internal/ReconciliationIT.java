package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.billing.internal.PaymentProvider.ProviderPayment;
import com.engineerhub.billing.internal.PaymentProvider.ProviderRefundRow;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
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
 * Reconciliation against the provider (a test double): every kind of drift, the two fixes an admin
 * can apply, quiet when nothing differs, safe against concurrent runs and an unreachable provider,
 * and the expiry of abandoned checkouts that belongs with it.
 */
@IntegrationTest
class ReconciliationIT {

  private static final String WEBHOOK_SECRET = "it-webhook-secret-not-real";
  private static final String BASE = "/api/v1/admin/billing";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeRazorpayProvider provider;

  @BeforeEach
  @AfterEach
  void isolate() {
    provider.reset();
    // payments, findings and runs of other tests must not show up in this one's comparison
    jdbc.sql(
            "update billing.payments set captured_at = now() - interval '30 days' where captured_at > now() - interval '3 days'")
        .update();
    jdbc.sql(
            "update billing.reconciliation_findings set status = 'resolved', resolution = 'acknowledged', resolved_at = now() where status = 'open'")
        .update();
    jdbc.sql(
            "update billing.reconciliation_runs set status = 'error', finished_at = now() where status = 'running'")
        .update();
    jdbc.sql(
            "update billing.refund_settings set policy_text = null, request_window_days = null, revoke_access_on_refund = true, approval_threshold_minor = null, auto_detect_duplicates = true where id = 1")
        .update();
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

  /** {one-time month price id (29950), recurring month price id (24900)} */
  private String[] catalog() throws Exception {
    String code = "rc-" + Ids.newId().toString().substring(24);
    String plan =
        json(call(
                    post(BASE + "/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Rec %s\"}".formatted(code, code))
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
        if (p.get("billingMode").asString().equals(specs[n][0])) ids[n] = p.get("id").asString();
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

  /** Starts a purchase and returns its provider order id (no payment yet). */
  private String startOrder(JwtRequestPostProcessor u, String priceId) throws Exception {
    return json(call(
                post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(priceId))
            .andExpect(status().isCreated()))
        .get("orderId")
        .asString();
  }

  private String capture(String order) throws Exception {
    String pay = "pay_RC" + Ids.newId().toString().substring(20).replace("-", "");
    signed(
            "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"%s\",\"amount\":29950,\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"%s\"}}}}"
                .formatted(pay, order),
            "evt_" + pay)
        .andExpect(status().isOk());
    return pay;
  }

  private UUID paymentId(String ref) {
    return jdbc.sql("select id from billing.payments where provider_payment_ref = :r")
        .param("r", ref)
        .query(UUID.class)
        .single();
  }

  private boolean premium(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/access"), u, null)).get("premium").asBoolean();
  }

  private JsonNode run() throws Exception {
    return json(call(post(BASE + "/reconciliation/run"), admin, null).andExpect(status().isOk()));
  }

  private JsonNode finding(String kind, String ref) throws Exception {
    for (JsonNode f : json(call(get(BASE + "/reconciliation"), admin, null)).get("openFindings")) {
      if (f.get("kind").asString().equals(kind) && f.get("providerRef").asString().equals(ref))
        return f;
    }
    return null;
  }

  private void list(String id, long amount, String status, String order, long refunded) {
    provider.listedPayments.add(new ProviderPayment(id, amount, "INR", status, order, refunded));
  }

  // ------------------------------------------------------------------ access and basic behaviour

  @Test
  void onlyBillingManagersMayReadOrRun() throws Exception {
    var learner = who(sub());
    UUID any = UUID.randomUUID();
    for (JwtRequestPostProcessor denied : new JwtRequestPostProcessor[] {null, learner, manager}) {
      int expected = denied == null ? 401 : 403;
      call(get(BASE + "/reconciliation"), denied, null).andExpect(status().is(expected));
      call(post(BASE + "/reconciliation/run"), denied, null).andExpect(status().is(expected));
      call(post(BASE + "/reconciliation/findings/" + any + "/apply"), denied, null)
          .andExpect(status().is(expected));
      call(
              post(BASE + "/reconciliation/findings/" + any + "/resolve"),
              denied,
              "{\"note\":\"long enough\"}")
          .andExpect(status().is(expected));
    }
    call(post(BASE + "/reconciliation/findings/" + any + "/apply"), admin, null)
        .andExpect(status().isNotFound());
    call(
            post(BASE + "/reconciliation/findings/" + any + "/resolve"),
            admin,
            "{\"note\":\"long enough\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void withoutPaymentCredentialsARunIsSkippedAndSaysSo() throws Exception {
    provider.disabled = true;
    JsonNode r = run();
    assertThat(r.get("status").asString()).isEqualTo("skipped");
    assertThat(r.get("note").asString()).contains("not configured");
    assertThat(r.get("findingsNew").asInt()).isZero();
  }

  @Test
  void whenEverythingAgreesTheRunIsOkAndQuiet() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String pay = capture(startOrder(u, c[0]));
    list(pay, 29950, "captured", "order_x", 0);
    jdbc.sql(
            "update billing.payments set captured_at = now() - interval '5 hours' where provider_payment_ref = :r")
        .param("r", pay)
        .update();
    JsonNode r = run();
    assertThat(r.get("status").asString()).isEqualTo("ok");
    assertThat(r.get("findingsNew").asInt()).isZero();
    assertThat(r.get("providerPayments").asInt()).isEqualTo(1);
    assertThat(json(call(get(BASE + "/reconciliation"), admin, null)).get("openFindings"))
        .isEmpty();
  }

  // ------------------------------------------------------------------ drift and its fixes

  @Test
  void aLostWebhookIsFoundAndCanBeAppliedExactlyOnce() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String order =
        startOrder(u, c[0]); // the buyer paid, but the "payment.captured" webhook never arrived
    String pay = "pay_RClost" + Ids.newId().toString().substring(26);
    list(pay, 29950, "captured", order, 0);
    assertThat(premium(u)).isFalse();

    JsonNode r = run();
    assertThat(r.get("status").asString()).isEqualTo("drift");
    assertThat(r.get("findingsNew").asInt()).isEqualTo(1);
    JsonNode f = finding("missing_payment", pay);
    assertThat(f).isNotNull();
    assertThat(f.get("applicable").asBoolean()).isTrue();
    assertThat(premium(u)).isFalse(); // a run never changes money records by itself

    // a second run does not report it again
    JsonNode again = run();
    assertThat(again.get("status").asString()).isEqualTo("drift");
    assertThat(again.get("findingsNew").asInt()).isZero();
    assertThat(
            jdbc.sql(
                    "select count(*) from billing.reconciliation_findings where provider_ref = :r and status = 'open'")
                .param("r", pay)
                .query(Long.class)
                .single())
        .isEqualTo(1);

    call(post(BASE + "/reconciliation/findings/" + f.get("id").asString() + "/apply"), admin, null)
        .andExpect(status().isNoContent());
    assertThat(premium(u)).isTrue();
    assertThat(
            jdbc.sql("select count(*) from billing.payments where provider_payment_ref = :r")
                .param("r", pay)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(finding("missing_payment", pay)).isNull();
    call(post(BASE + "/reconciliation/findings/" + f.get("id").asString() + "/apply"), admin, null)
        .andExpect(status().isNotFound());
    assertThat(run().get("status").asString()).isEqualTo("ok");
    assertThat(
            jdbc.sql(
                    "select count(*) from admin.audit_logs where action = 'billing.reconciliation.finding.applied'")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void aPaymentNobodyKnowsIsReportedAndOnlyAcknowledgedNeverApplied() throws Exception {
    String pay = "pay_RCunk" + Ids.newId().toString().substring(26);
    list(pay, 500, "captured", "order_somebody_else", 0);
    assertThat(run().get("status").asString()).isEqualTo("drift");
    JsonNode f = finding("unknown_provider_payment", pay);
    assertThat(f.get("applicable").asBoolean()).isFalse();
    String url = BASE + "/reconciliation/findings/" + f.get("id").asString();
    call(post(url + "/apply"), admin, null).andExpect(status().isConflict());
    call(post(url + "/resolve"), admin, "{\"note\":\"x\"}").andExpect(status().isBadRequest());
    call(post(url + "/resolve"), admin, "{\"note\":\"checked in the provider dashboard\"}")
        .andExpect(status().isNoContent());
    assertThat(finding("unknown_provider_payment", pay)).isNull();
    call(post(url + "/resolve"), admin, "{\"note\":\"checked in the provider dashboard\"}")
        .andExpect(status().isNotFound());
    // an acknowledged drift that is still true is reported again by the next run
    assertThat(run().get("findingsNew").asInt()).isEqualTo(1);
  }

  @Test
  void amountAndRefundTotalsThatDisagreeAreReported() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String pay = capture(startOrder(u, c[0]));
    jdbc.sql(
            "update billing.payments set captured_at = now() - interval '5 hours' where provider_payment_ref = :r")
        .param("r", pay)
        .update();
    list(pay, 30000, "captured", "order_x", 4000);
    assertThat(run().get("findingsNew").asInt()).isEqualTo(2);
    assertThat(finding("amount_mismatch", pay)).isNotNull();
    assertThat(finding("refund_amount_mismatch", pay).get("amountMinor").asLong()).isEqualTo(4000);
    assertThat(premium(u)).isTrue(); // nothing was changed
  }

  @Test
  void aRefundTheProviderFinishedIsAppliedWhenOurWebhookWasLost() throws Exception {
    provider.refundStatus = "pending";
    String[] c = catalog();
    var u = who(sub());
    String pay = capture(startOrder(u, c[0]));
    UUID payment = paymentId(pay);
    jdbc.sql("update billing.payments set captured_at = now() - interval '5 hours' where id = :p")
        .param("p", payment)
        .update();
    String req =
        json(call(
                    post("/api/v1/billing/refund-requests"),
                    u,
                    "{\"paymentId\":\"%s\",\"reason\":\"other\"}".formatted(payment))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(post(BASE + "/refunds/" + req + "/approve"), admin, "{\"note\":\"approved in test\"}")
        .andExpect(status().isOk());
    String refundRef = provider.refunds.get(0).refundId();
    jdbc.sql(
            "update billing.refunds set updated_at = now() - interval '1 hour' where provider_refund_ref = :r")
        .param("r", refundRef)
        .update();
    list(pay, 29950, "refunded", "order_x", 29950);
    provider.listedRefunds.add(new ProviderRefundRow(refundRef, pay, 29950, "processed"));

    run();
    JsonNode f = finding("refund_stuck", refundRef);
    assertThat(f).isNotNull();
    assertThat(f.get("applicable").asBoolean()).isTrue();
    assertThat(
            jdbc.sql("select status from billing.refund_requests where id = :i")
                .param("i", UUID.fromString(req))
                .query(String.class)
                .single())
        .isEqualTo("processing");
    call(post(BASE + "/reconciliation/findings/" + f.get("id").asString() + "/apply"), admin, null)
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.sql("select status from billing.refund_requests where id = :i")
                .param("i", UUID.fromString(req))
                .query(String.class)
                .single())
        .isEqualTo("processed");
    assertThat(
            jdbc.sql("select status from billing.payments where id = :p")
                .param("p", payment)
                .query(String.class)
                .single())
        .isEqualTo("refunded");
    assertThat(premium(u)).isFalse();
    assertThat(run().get("status").asString()).isEqualTo("ok");
  }

  @Test
  void aPaymentTheProviderDoesNotListIsReportedOnlyWhenTheListingIsComplete() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String pay = capture(startOrder(u, c[0]));
    jdbc.sql(
            "update billing.payments set captured_at = now() - interval '5 hours' where provider_payment_ref = :r")
        .param("r", pay)
        .update();

    provider.listingComplete = false; // cut off: absence proves nothing
    JsonNode partial = run();
    assertThat(partial.get("status").asString()).isEqualTo("partial");
    assertThat(partial.get("note").asString()).contains("cut off");
    assertThat(finding("missing_at_provider", pay)).isNull();

    provider.listingComplete = true;
    assertThat(run().get("status").asString()).isEqualTo("drift");
    JsonNode f = finding("missing_at_provider", pay);
    assertThat(f).isNotNull();
    assertThat(f.get("applicable").asBoolean()).isFalse();
  }

  // ------------------------------------------------------------------ robustness

  @Test
  void anUnreachableProviderIsAnErrorRunWithoutDetailsAndNoFindings() throws Exception {
    provider.failNextListing = true;
    JsonNode r = run();
    assertThat(r.get("status").asString()).isEqualTo("error");
    assertThat(r.get("note").asString()).isEqualTo("The payment provider could not be reached.");
    assertThat(json(call(get(BASE + "/reconciliation"), admin, null)).get("openFindings"))
        .isEmpty();
  }

  @Test
  void onlyOneRunAtATimeAndACrashedRunDoesNotBlockForever() throws Exception {
    jdbc.sql(
            "insert into billing.reconciliation_runs (id, trigger, started_at, window_from, window_to, status) values (:id, 'manual', now(), now() - interval '2 days', now(), 'running')")
        .param("id", Ids.newId())
        .update();
    call(post(BASE + "/reconciliation/run"), admin, null).andExpect(status().isConflict());
    // the same row, but abandoned two hours ago: cleaned up, and the run goes ahead
    jdbc.sql(
            "update billing.reconciliation_runs set started_at = now() - interval '2 hours' where status = 'running'")
        .update();
    assertThat(run().get("status").asString()).isIn("ok", "drift", "partial", "skipped");
    assertThat(
            jdbc.sql("select count(*) from billing.reconciliation_runs where status = 'running'")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql(
                    "select count(*) from billing.reconciliation_runs where note like 'Abandoned%'")
                .query(Long.class)
                .single())
        .isGreaterThanOrEqualTo(1);
  }

  // ------------------------------------------------------------------ abandoned checkouts

  @Test
  void anAbandonedPurchaseExpiresButAPaymentThatStillArrivesIsHonoured() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String order = startOrder(u, c[0]);
    UUID intent =
        jdbc.sql("select id from billing.purchase_intents where provider_order_ref = :o")
            .param("o", order)
            .query(UUID.class)
            .single();
    String open =
        jdbc.sql("select status from billing.purchase_intents where id = :i")
            .param("i", intent)
            .query(String.class)
            .single();
    assertThat(open).isEqualTo("payment_pending");
    jdbc.sql(
            "update billing.purchase_intents set expires_at = now() - interval '1 minute' where id = :i")
        .param("i", intent)
        .update();
    JsonNode r = run();
    assertThat(r.get("intentsExpired").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.sql("select status from billing.purchase_intents where id = :i")
                .param("i", intent)
                .query(String.class)
                .single())
        .isEqualTo("expired");
    assertThat(premium(u)).isFalse(); // abandoning grants nothing

    capture(order); // the buyer paid after all: money taken is never ignored
    assertThat(premium(u)).isTrue();
    assertThat(
            jdbc.sql("select status from billing.purchase_intents where id = :i")
                .param("i", intent)
                .query(String.class)
                .single())
        .isEqualTo("paid");
    // a paid purchase is never expired
    jdbc.sql(
            "update billing.purchase_intents set expires_at = now() - interval '1 day' where id = :i")
        .param("i", intent)
        .update();
    run();
    assertThat(
            jdbc.sql("select status from billing.purchase_intents where id = :i")
                .param("i", intent)
                .query(String.class)
                .single())
        .isEqualTo("paid");
  }

  @Test
  void anAbandonedSubscriptionCheckoutDoesNotBlockStartingAgain() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String body = "{\"planPriceId\":\"%s\"}".formatted(c[1]);
    JsonNode first =
        json(call(post("/api/v1/billing/subscriptions"), u, body).andExpect(status().isCreated()));
    String firstId = first.get("subscriptionId").asString();
    // a fresh checkout is respected (the user may still be paying)
    call(post("/api/v1/billing/subscriptions"), u, body).andExpect(status().isConflict());
    // 31 minutes of silence and no payment: starting again supersedes it
    jdbc.sql(
            "update billing.subscriptions set updated_at = now() - interval '31 minutes' where id = :i")
        .param("i", UUID.fromString(firstId))
        .update();
    JsonNode second =
        json(call(post("/api/v1/billing/subscriptions"), u, body).andExpect(status().isCreated()));
    assertThat(second.get("subscriptionId").asString()).isNotEqualTo(firstId);
    assertThat(
            jdbc.sql("select status from billing.subscriptions where id = :i")
                .param("i", UUID.fromString(firstId))
                .query(String.class)
                .single())
        .isEqualTo("failed");
  }

  @Test
  void theNightlyRunExpiresSubscriptionCheckoutsNobodyFinishedButNeverOnesThatWerePaid()
      throws Exception {
    String[] c = catalog();
    var stale = who(sub());
    var paid = who(sub());
    String a =
        json(call(
                    post("/api/v1/billing/subscriptions"),
                    stale,
                    "{\"planPriceId\":\"%s\"}".formatted(c[1]))
                .andExpect(status().isCreated()))
            .get("subscriptionId")
            .asString();
    JsonNode p =
        json(
            call(
                    post("/api/v1/billing/subscriptions"),
                    paid,
                    "{\"planPriceId\":\"%s\"}".formatted(c[1]))
                .andExpect(status().isCreated()));
    signed(
            "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"%s\"}},\"payment\":{\"entity\":{\"id\":\"pay_RCsub%s\",\"amount\":24900,\"currency\":\"INR\",\"status\":\"captured\"}}}}"
                .formatted(
                    p.get("providerSubscriptionId").asString(),
                    Ids.newId().toString().substring(28)),
            "evt_" + Ids.newId())
        .andExpect(status().isOk());
    jdbc.sql(
            "update billing.subscriptions set updated_at = now() - interval '25 hours' where id in (:a, :b)")
        .param("a", UUID.fromString(a))
        .param("b", UUID.fromString(p.get("subscriptionId").asString()))
        .update();
    // a pending checkout that somehow already has a payment must never be failed either
    var odd = who(sub());
    JsonNode o =
        json(
            call(
                    post("/api/v1/billing/subscriptions"),
                    odd,
                    "{\"planPriceId\":\"%s\"}".formatted(c[1]))
                .andExpect(status().isCreated()));
    UUID oddId = UUID.fromString(o.get("subscriptionId").asString());
    jdbc.sql(
            "insert into billing.payments (id, subscription_id, user_id, provider, provider_payment_ref, amount_minor, currency, status, captured_at, created_at) select :id, s.id, s.user_id, 'razorpay', :ref, 24900, 'INR', 'captured', now() - interval '30 days', now() from billing.subscriptions s where s.id = :sid")
        .param("id", Ids.newId())
        .param("ref", "pay_RCodd" + Ids.newId().toString().substring(28))
        .param("sid", oddId)
        .update();
    jdbc.sql(
            "update billing.subscriptions set updated_at = now() - interval '25 hours' where id = :i")
        .param("i", oddId)
        .update();
    run();
    assertThat(
            jdbc.sql("select status from billing.subscriptions where id = :i")
                .param("i", UUID.fromString(a))
                .query(String.class)
                .single())
        .isEqualTo("failed");
    assertThat(
            jdbc.sql("select status from billing.subscriptions where id = :i")
                .param("i", UUID.fromString(p.get("subscriptionId").asString()))
                .query(String.class)
                .single())
        .isEqualTo("active");
    assertThat(
            jdbc.sql("select status from billing.subscriptions where id = :i")
                .param("i", oddId)
                .query(String.class)
                .single())
        .isEqualTo("pending");
  }
}
