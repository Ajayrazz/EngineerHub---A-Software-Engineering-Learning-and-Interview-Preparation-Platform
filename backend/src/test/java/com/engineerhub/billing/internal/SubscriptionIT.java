package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * Auto-renewing subscriptions: start, confirm, the webhook that is the only thing granting access
 * (first payment and every renewal), the guarded lifecycle, and cancel at cycle end. The provider
 * is a test double; signatures are the real code.
 */
@IntegrationTest
class SubscriptionIT {

  private static final String KEY_SECRET = "it-key-secret-not-real";
  private static final String WEBHOOK_SECRET = "it-webhook-secret-not-real";

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired FakeRazorpayProvider provider;

  @BeforeEach
  void reset() {
    provider.reset();
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

  private ResultActions call(
      MockHttpServletRequestBuilder b, JwtRequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req = who == null ? b : b.with(who);
    return mvc.perform(
        body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode json(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  /** {plan id, RECURRING month price, RECURRING year price, ONE_TIME month price} */
  private String[] catalog() throws Exception {
    String code = "sp-" + Ids.newId().toString().substring(24);
    String plan =
        json(call(
                    post("/api/v1/admin/billing/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Sub %s\"}".formatted(code, code))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(
            put("/api/v1/admin/billing/plans/" + plan + "/entitlements"),
            admin,
            "{\"codes\":[\"premium_content\"]}")
        .andExpect(status().isOk());
    String[] ids = new String[4];
    ids[0] = plan;
    String[][] specs = {
      {"RECURRING", "month", "24900"},
      {"RECURRING", "year", "249000"},
      {"ONE_TIME", "month", "29900"}
    };
    for (int n = 0; n < specs.length; n++) {
      String body =
          "{\"billingMode\":\"%s\",\"periodUnit\":\"%s\",\"amountInclTaxMinor\":%s}"
              .formatted((Object[]) specs[n]);
      for (JsonNode p :
          json(call(post("/api/v1/admin/billing/plans/" + plan + "/prices"), admin, body)
                  .andExpect(status().isCreated()))
              .get("prices")) {
        String id = p.get("id").asString();
        if (p.get("billingMode").asString().equals(specs[n][0])
            && p.get("periodUnit").asString().equals(specs[n][1])
            && p.get("amountInclTaxMinor").asLong() == Long.parseLong(specs[n][2])) {
          ids[n + 1] = id;
        }
      }
    }
    return ids;
  }

  private JsonNode subscribe(JwtRequestPostProcessor u, String priceId, String idem)
      throws Exception {
    var b = post("/api/v1/billing/subscriptions");
    if (idem != null) b = b.header("Idempotency-Key", idem);
    return json(
        call(b, u, "{\"planPriceId\":\"%s\"}".formatted(priceId)).andExpect(status().isCreated()));
  }

  private boolean premium(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/access"), u, null)).get("premium").asBoolean();
  }

  private String statusOf(String subscriptionId) {
    return jdbc.sql("select status from billing.subscriptions where id = :id")
        .param("id", UUID.fromString(subscriptionId))
        .query(String.class)
        .single();
  }

  private long count(String sql, Object... args) {
    var q = jdbc.sql(sql);
    for (int i = 0; i < args.length; i++) q = q.param("p" + i, args[i]);
    return q.query(Long.class).single();
  }

  private ResultActions webhook(String body, String signature, String eventId) throws Exception {
    MockHttpServletRequestBuilder b =
        post("/api/v1/webhooks/razorpay")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8));
    if (signature != null) b = b.header("X-Razorpay-Signature", signature);
    if (eventId != null) b = b.header("X-Razorpay-Event-Id", eventId);
    return mvc.perform(b);
  }

  private ResultActions signed(String body, String eventId) throws Exception {
    return webhook(
        body,
        RazorpaySignatures.hmacHex(WEBHOOK_SECRET, body.getBytes(StandardCharsets.UTF_8)),
        eventId);
  }

  private static String chargedBody(
      String subRef, String payId, long amount, String currency, String payStatus) {
    return "{\"entity\":\"event\",\"event\":\"subscription.charged\",\"contains\":[\"subscription\",\"payment\"],\"payload\":{\"subscription\":{\"entity\":{\"id\":\"%s\",\"entity\":\"subscription\",\"status\":\"active\",\"paid_count\":1}},\"payment\":{\"entity\":{\"id\":\"%s\",\"entity\":\"payment\",\"amount\":%d,\"currency\":\"%s\",\"status\":\"%s\"}}}}"
        .formatted(subRef, payId, amount, currency, payStatus);
  }

  private static String eventBody(String event, String subRef) {
    return "{\"entity\":\"event\",\"event\":\"%s\",\"contains\":[\"subscription\"],\"payload\":{\"subscription\":{\"entity\":{\"id\":\"%s\",\"entity\":\"subscription\"}}}}"
        .formatted(event, subRef);
  }

  private String charge(String subRef, long amount) throws Exception {
    String pay = "pay_SB" + Ids.newId().toString().substring(20).replace("-", "");
    signed(chargedBody(subRef, pay, amount, "INR", "captured"), "evt_" + pay)
        .andExpect(status().isOk());
    return pay;
  }

  private String event(String name, String subRef) throws Exception {
    String id = "evt_" + Ids.newId();
    return json(signed(eventBody(name, subRef), id).andExpect(status().isOk()))
        .get("status")
        .asString();
  }

  // ------------------------------------------------------------------ starting

  @Test
  void startNeedsLoginAndOnlyAcceptsRecurringPrices() throws Exception {
    String[] c = catalog();
    call(post("/api/v1/billing/subscriptions"), null, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isUnauthorized());
    var u = who(sub());
    // a one-time price must go through the pass flow, and the reverse
    call(post("/api/v1/billing/subscriptions"), u, "{\"planPriceId\":\"%s\"}".formatted(c[3]))
        .andExpect(status().isConflict());
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isConflict());
    call(
            post("/api/v1/billing/subscriptions"),
            u,
            "{\"planPriceId\":\"%s\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    call(post("/api/v1/billing/subscriptions"), u, "{}").andExpect(status().isBadRequest());
    assertThat(provider.subscriptions).isEmpty();
  }

  @Test
  void startIsPricedByTheServerAndSendsNothingPersonalToTheProvider() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode m = subscribe(u, c[1], null);
    assertThat(m.get("providerSubscriptionId").asString()).startsWith("sub_");
    assertThat(m.get("amountMinor").asLong()).isEqualTo(24900);
    assertThat(m.get("currency").asString()).isEqualTo("INR");
    assertThat(m.get("periodUnit").asString()).isEqualTo("month");
    assertThat(m.get("mode").asString()).isEqualTo("test");
    assertThat(m.get("keyId").asString()).startsWith("rzp_test_");
    assertThat(provider.plans).hasSize(1);
    assertThat(provider.plans.get(0).period()).isEqualTo("month");
    assertThat(provider.plans.get(0).amountMinor()).isEqualTo(24900);
    assertThat(provider.subscriptions).hasSize(1);
    assertThat(provider.subscriptions.get(0).totalCycles()).isEqualTo(120);
    assertThat(provider.subscriptions.get(0).notes())
        .containsOnlyKeys("subscriptionId"); // no e-mail, name or user id
    assertThat(premium(u)).isFalse(); // starting grants nothing

    var y = who(sub());
    subscribe(y, c[2], null);
    assertThat(provider.subscriptions.get(1).totalCycles()).isEqualTo(10);
    assertThat(provider.plans.get(1).period()).isEqualTo("year");
    assertThat(provider.plans.get(1).amountMinor()).isEqualTo(249000);
  }

  @Test
  void theProviderPlanOfAPriceIsCreatedOnceAndReused() throws Exception {
    String[] c = catalog();
    subscribe(who(sub()), c[1], null);
    subscribe(who(sub()), c[1], null);
    assertThat(provider.plans).hasSize(1);
    assertThat(provider.subscriptions.get(0).planId())
        .isEqualTo(provider.subscriptions.get(1).planId());
  }

  @Test
  void onlyOneLiveSubscriptionPerUserAndPlan() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    subscribe(u, c[1], null);
    call(post("/api/v1/billing/subscriptions"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isConflict());
    call(post("/api/v1/billing/subscriptions"), u, "{\"planPriceId\":\"%s\"}".formatted(c[2]))
        .andExpect(status().isConflict()); // the yearly price of the same plan counts too
    subscribe(who(sub()), c[1], null); // another user is fine
    assertThat(provider.subscriptions).hasSize(2);
  }

  @Test
  void aProviderFailureLeavesNoLiveSubscriptionAndTheUserCanRetry() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    provider.failNextSubscription = true;
    call(post("/api/v1/billing/subscriptions"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isServiceUnavailable());
    assertThat(count("select count(*) from billing.subscriptions where status = 'failed'"))
        .isGreaterThanOrEqualTo(1);
    subscribe(u, c[1], null); // the failed row does not block a retry
  }

  @Test
  void paymentsOffMeansNoSubscription() throws Exception {
    String[] c = catalog();
    provider.disabled = true;
    call(
            post("/api/v1/billing/subscriptions"),
            who(sub()),
            "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isServiceUnavailable());
    assertThat(provider.subscriptions).isEmpty();
  }

  @Test
  void anIdempotencyKeyReturnsTheSameSubscriptionAndNeverAnother() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String key = "sub-key-" + Ids.newId();
    JsonNode a = subscribe(u, c[1], key);
    JsonNode b = subscribe(u, c[1], key);
    assertThat(b.get("subscriptionId").asString()).isEqualTo(a.get("subscriptionId").asString());
    assertThat(provider.subscriptions).hasSize(1);
    call(
            post("/api/v1/billing/subscriptions").header("Idempotency-Key", key),
            u,
            "{\"planPriceId\":\"%s\"}".formatted(c[2]))
        .andExpect(status().isConflict());
    call(
            post("/api/v1/billing/subscriptions").header("Idempotency-Key", "short"),
            who(sub()),
            "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ confirm (never grants)

  @Test
  void confirmChecksTheSignatureAgainstTheStoredSubscriptionAndNeverGrants() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    String ref = s.get("providerSubscriptionId").asString();
    String pay = "pay_SBconfirm1";
    String good =
        RazorpaySignatures.hmacHex(KEY_SECRET, (pay + "|" + ref).getBytes(StandardCharsets.UTF_8));
    String url = "/api/v1/billing/subscriptions/" + id + "/confirm";
    String body =
        "{\"razorpaySubscriptionId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}";

    call(post(url), u, body.formatted(ref, pay, good))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signatureValid").value(true))
        .andExpect(jsonPath("$.subscription.paid").value(false));
    assertThat(premium(u)).isFalse(); // a valid browser callback still grants nothing

    // a signature made for ANOTHER subscription does not verify, even with its id in the body
    JsonNode other = subscribe(who(sub()), c[1], null);
    String otherRef = other.get("providerSubscriptionId").asString();
    String otherSig =
        RazorpaySignatures.hmacHex(
            KEY_SECRET, (pay + "|" + otherRef).getBytes(StandardCharsets.UTF_8));
    call(post(url), u, body.formatted(ref, pay, otherSig))
        .andExpect(jsonPath("$.signatureValid").value(false));
    call(post(url), u, body.formatted(otherRef, pay, otherSig))
        .andExpect(jsonPath("$.signatureValid").value(false)); // not the id WE stored
    // a signature that IS valid for our stored id, sent with a different subscription id: refused
    call(post(url), u, body.formatted(otherRef, pay, good))
        .andExpect(jsonPath("$.signatureValid").value(false));
    call(post(url), u, body.formatted(ref, pay, "00" + good.substring(2)))
        .andExpect(jsonPath("$.signatureValid").value(false));
    // someone else's subscription looks missing
    call(post(url), who(sub()), body.formatted(ref, pay, good)).andExpect(status().isNotFound());
  }

  @Test
  void aSubscriptionIsVisibleOnlyToItsOwnerAndNeverCached() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String id = subscribe(u, c[1], null).get("subscriptionId").asString();
    call(get("/api/v1/billing/subscriptions/" + id), u, null)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.paid").value(false));
    call(get("/api/v1/billing/subscriptions/" + id), who(sub()), null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/billing/subscriptions/" + id), null, null)
        .andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ the webhook grants access

  @Test
  void aVerifiedChargeGrantsAccessOnceAndRenewalsExtendFromTheEndOfThePeriod() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    String ref = s.get("providerSubscriptionId").asString();

    String pay1 = charge(ref, 24900);
    assertThat(premium(u)).isTrue();
    assertThat(statusOf(id)).isEqualTo("active");
    assertThat(
            count(
                "select count(*) from billing.payments where subscription_id = :p0",
                UUID.fromString(id)))
        .isEqualTo(1);

    // redelivery (same event id) and a replay under another event id grant nothing more
    String body = chargedBody(ref, pay1, 24900, "INR", "captured");
    assertThat(
            json(signed(body, "evt_" + pay1).andExpect(status().isOk())).get("status").asString())
        .isEqualTo("duplicate");
    assertThat(
            json(signed(body, "evt_other_" + Ids.newId()).andExpect(status().isOk()))
                .get("status")
                .asString())
        .isEqualTo("duplicate");
    assertThat(
            count(
                "select count(*) from billing.access_periods where source_type = 'payment' and user_id = (select user_id from billing.subscriptions where id = :p0)",
                UUID.fromString(id)))
        .isEqualTo(1);

    // the next cycle's charge appends a period that starts exactly where the first one ends
    charge(ref, 24900);
    List<Timestamp[]> periods =
        jdbc.sql(
                "select starts_at, ends_at from billing.access_periods where user_id = (select user_id from billing.subscriptions where id = :id) order by starts_at")
            .param("id", UUID.fromString(id))
            .query((rs, i) -> new Timestamp[] {rs.getTimestamp(1), rs.getTimestamp(2)})
            .list();
    assertThat(periods).hasSize(2);
    assertThat(periods.get(1)[0]).isEqualTo(periods.get(0)[1]);

    JsonNode summary = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(summary.get("payments")).hasSize(2);
    assertThat(summary.get("payments").get(0).get("planName").asString()).startsWith("Sub ");
    assertThat(summary.get("subscription").get("status").asString()).isEqualTo("active");
    assertThat(summary.get("subscription").get("amountMinor").asLong()).isEqualTo(24900);
    assertThat(summary.get("subscription").get("renewsOn").asString())
        .isEqualTo(summary.get("validThrough").asString());
  }

  @Test
  void anAnnualSubscriptionsChargeGrantsAYearNotAMonth() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[2], null); // the yearly auto-renewing price
    String ref = s.get("providerSubscriptionId").asString();
    charge(ref, 249000);
    assertThat(premium(u)).isTrue();
    Timestamp[] period =
        jdbc.sql(
                "select starts_at, ends_at from billing.access_periods where user_id = (select user_id from billing.subscriptions where id = :id)")
            .param("id", UUID.fromString(s.get("subscriptionId").asString()))
            .query((rs, i) -> new Timestamp[] {rs.getTimestamp(1), rs.getTimestamp(2)})
            .single();
    assertThat(period[1].toInstant())
        .isEqualTo(period[0].toInstant().atZone(java.time.ZoneOffset.UTC).plusYears(1).toInstant());
    // the second yearly charge starts where the first year ends
    charge(ref, 249000);
    assertThat(
            count(
                "select count(*) from billing.access_periods where user_id = (select user_id from billing.subscriptions where id = :p0)",
                UUID.fromString(s.get("subscriptionId").asString())))
        .isEqualTo(2);
  }

  @Test
  void aForgedOrUnsignedSubscriptionEventChangesNothing() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String ref = subscribe(u, c[1], null).get("providerSubscriptionId").asString();
    String body = chargedBody(ref, "pay_SBforged", 24900, "INR", "captured");
    webhook(body, "0".repeat(64), "evt_forged_" + Ids.newId()).andExpect(status().isUnauthorized());
    webhook(body, null, "evt_unsigned_" + Ids.newId()).andExpect(status().isUnauthorized());
    assertThat(premium(u)).isFalse();
    assertThat(
            count(
                "select count(*) from billing.payments where provider_payment_ref = 'pay_SBforged'"))
        .isZero();
  }

  @Test
  void aChargeWithTheWrongAmountOrCurrencyGrantsNothingAndFlagsTheSubscription() throws Exception {
    String[] c = catalog();
    for (Object[] bad : new Object[][] {{1000L, "INR"}, {24900L, "USD"}}) {
      var u = who(sub());
      JsonNode s = subscribe(u, c[1], null);
      String ref = s.get("providerSubscriptionId").asString();
      String pay = "pay_SBbad" + Ids.newId().toString().substring(30);
      String outcome =
          json(signed(
                      chargedBody(ref, pay, (Long) bad[0], (String) bad[1], "captured"),
                      "evt_" + pay)
                  .andExpect(status().isOk()))
              .get("status")
              .asString();
      assertThat(outcome).isEqualTo("amount_mismatch");
      assertThat(premium(u)).isFalse();
      assertThat(statusOf(s.get("subscriptionId").asString())).isEqualTo("needs_review");
      assertThat(
              count("select count(*) from billing.payments where provider_payment_ref = :p0", pay))
          .isZero();
    }
  }

  @Test
  void unknownMalformedAndUncapturedChargesAreIgnored() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String ref = subscribe(u, c[1], null).get("providerSubscriptionId").asString();
    assertThat(
            json(signed(
                        chargedBody(
                            "sub_unknown",
                            "pay_SBu1" + Ids.newId().toString().substring(24),
                            24900,
                            "INR",
                            "captured"),
                        "evt_u1_" + Ids.newId())
                    .andExpect(status().isOk()))
                .get("status")
                .asString())
        .isEqualTo("unknown_subscription");
    assertThat(
            json(signed(
                        chargedBody(
                            ref,
                            "pay_SBu2" + Ids.newId().toString().substring(24),
                            24900,
                            "INR",
                            "failed"),
                        "evt_u2_" + Ids.newId())
                    .andExpect(status().isOk()))
                .get("status")
                .asString())
        .isEqualTo("ignored_not_captured");
    assertThat(
            json(signed(
                        chargedBody(
                            "",
                            "pay_SBu3" + Ids.newId().toString().substring(24),
                            24900,
                            "INR",
                            "captured"),
                        "evt_u3_" + Ids.newId())
                    .andExpect(status().isOk()))
                .get("status")
                .asString())
        .isEqualTo("ignored_malformed");
    assertThat(premium(u)).isFalse();
  }

  // ------------------------------------------------------------------ guarded lifecycle

  @Test
  void lifecycleEventsNeverRegressAndNeverGrant() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    String ref = s.get("providerSubscriptionId").asString();

    assertThat(event("subscription.authenticated", ref)).isEqualTo("ignored_event");
    assertThat(event("subscription.pending", ref))
        .isEqualTo("ignored_no_transition"); // not active yet
    assertThat(event("subscription.activated", ref)).isEqualTo("applied");
    assertThat(statusOf(id)).isEqualTo("active");
    assertThat(premium(u)).isFalse(); // activation alone never grants access

    assertThat(event("subscription.pending", ref)).isEqualTo("applied"); // a renewal charge failed
    assertThat(statusOf(id)).isEqualTo("past_due");
    assertThat(json(call(get("/api/v1/me/billing"), u, null)).path("subscription").isMissingNode())
        .isFalse();

    charge(ref, 24900); // the provider's retry succeeded
    assertThat(statusOf(id)).isEqualTo("active");
    assertThat(premium(u)).isTrue();

    assertThat(event("subscription.halted", ref)).isEqualTo("applied");
    assertThat(statusOf(id)).isEqualTo("past_due");
    assertThat(premium(u))
        .isTrue(); // paid-through access is not taken back (no grace extension either)

    assertThat(event("subscription.cancelled", ref)).isEqualTo("applied");
    assertThat(statusOf(id)).isEqualTo("cancelled");
    // late and repeated events cannot revive it
    for (String late :
        new String[] {
          "subscription.activated",
          "subscription.pending",
          "subscription.halted",
          "subscription.completed"
        }) {
      assertThat(event(late, ref)).as(late).isEqualTo("ignored_no_transition");
    }
    assertThat(statusOf(id)).isEqualTo("cancelled");
    assertThat(event("subscription.paused", ref)).isEqualTo("ignored_event");
    assertThat(event("subscription.activated", "sub_unknown")).isEqualTo("unknown_subscription");
    subscribe(u, c[1], null); // the live slot is free again after the end
  }

  @Test
  void aChargeThatArrivesAfterCancellationStillBecomesAccessButDoesNotReviveIt() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    String ref = s.get("providerSubscriptionId").asString();
    event("subscription.cancelled", ref); // delivered before the charge it followed
    charge(ref, 24900); // the customer was charged: they get what they paid for
    assertThat(premium(u)).isTrue();
    assertThat(statusOf(id)).isEqualTo("cancelled");
  }

  @Test
  void completedIsReachedFromActive() throws Exception {
    String[] c = catalog();
    JsonNode s = subscribe(who(sub()), c[1], null);
    String ref = s.get("providerSubscriptionId").asString();
    charge(ref, 24900);
    assertThat(event("subscription.completed", ref)).isEqualTo("applied");
    assertThat(statusOf(s.get("subscriptionId").asString())).isEqualTo("completed");
    // a finished subscription is not "cancelled" afterwards by a late event
    assertThat(event("subscription.cancelled", ref)).isEqualTo("ignored_no_transition");
    assertThat(statusOf(s.get("subscriptionId").asString())).isEqualTo("completed");
  }

  // ------------------------------------------------------------------ cancel auto-renew

  @Test
  void cancelStopsRenewalAtCycleEndKeepsPaidAccessAndIsIdempotent() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    String ref = s.get("providerSubscriptionId").asString();
    String url = "/api/v1/billing/subscriptions/" + id + "/cancel";

    call(post(url), u, null).andExpect(status().isConflict()); // not running yet (pending)
    charge(ref, 24900);

    call(post(url), who(sub()), null).andExpect(status().isNotFound()); // not the owner
    call(post(url), null, null).andExpect(status().isUnauthorized());
    assertThat(provider.cancelledSubscriptions).isEmpty();

    call(post(url), u, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancel_scheduled"))
        .andExpect(jsonPath("$.cancelScheduled").value(true));
    assertThat(provider.cancelledSubscriptions).containsExactly(ref);
    assertThat(premium(u)).isTrue(); // paid access runs to the end

    call(post(url), u, null).andExpect(status().isOk()); // idempotent: no second provider call
    assertThat(provider.cancelledSubscriptions).hasSize(1);

    // the summary no longer promises a renewal, and the reminder is back
    JsonNode summary = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(summary.get("subscription").get("status").asString()).isEqualTo("cancel_scheduled");
    assertThat(summary.get("subscription").path("renewsOn").isMissingNode()).isTrue();
    // the provider ends it at the cycle end
    event("subscription.cancelled", ref);
    assertThat(statusOf(id)).isEqualTo("cancelled");
    assertThat(premium(u)).isTrue();
    // once the provider has ended it, the page still explains it while the paid access runs
    JsonNode ended = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(ended.get("subscription").get("status").asString()).isEqualTo("cancelled");
    assertThat(ended.get("subscription").path("renewsOn").isMissingNode()).isTrue();
  }

  @Test
  void aProviderFailureWhileCancellingLeavesTheSubscriptionRunning() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    charge(s.get("providerSubscriptionId").asString(), 24900);
    provider.failNextCancel = true;
    call(post("/api/v1/billing/subscriptions/" + id + "/cancel"), u, null)
        .andExpect(status().isServiceUnavailable());
    assertThat(statusOf(id))
        .isEqualTo("active"); // never "cancelled" when the provider did not agree
    call(post("/api/v1/billing/subscriptions/" + id + "/cancel"), u, null)
        .andExpect(status().isOk()); // and it can simply be retried
    assertThat(statusOf(id)).isEqualTo("cancel_scheduled");
  }

  // ------------------------------------------------------------------ billing summary

  @Test
  void anAutoRenewingSubscriptionGetsNoRenewNowReminderUntilItIsCancelled() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode s = subscribe(u, c[1], null);
    String id = s.get("subscriptionId").asString();
    charge(s.get("providerSubscriptionId").asString(), 24900);
    // 2 days before the end of the paid cycle (inside the 7 day window)
    Instant now = Instant.now();
    jdbc.sql(
            "update billing.access_periods set starts_at = :s, ends_at = :e where user_id = (select user_id from billing.subscriptions where id = :id)")
        .param("s", Timestamp.from(now.minus(Duration.ofDays(28))))
        .param("e", Timestamp.from(now.plus(Duration.ofDays(2))))
        .param("id", UUID.fromString(id))
        .update();

    JsonNode auto = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(auto.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(auto.get("renewalDue").asBoolean()).isFalse();
    assertThat(auto.get("subscription").get("renewsOn").asString())
        .isEqualTo(auto.get("validThrough").asString());

    call(post("/api/v1/billing/subscriptions/" + id + "/cancel"), u, null)
        .andExpect(status().isOk());
    JsonNode cancelled = json(call(get("/api/v1/me/billing"), u, null));
    assertThat(cancelled.get("status").asString()).isEqualTo("EXPIRING_SOON");
    assertThat(cancelled.get("renewalDue").asBoolean()).isTrue();
  }

  @Test
  void aUserWithoutASubscriptionHasNoSubscriptionBlock() throws Exception {
    JsonNode s = json(call(get("/api/v1/me/billing"), who(sub()), null));
    assertThat(s.path("subscription").isMissingNode()).isTrue();
  }
}
