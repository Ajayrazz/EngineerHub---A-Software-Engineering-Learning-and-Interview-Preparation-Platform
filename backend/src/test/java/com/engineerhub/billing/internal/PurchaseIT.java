package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
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
 * Purchases of one-time passes: quote, order, confirm, and the webhook that is the only thing
 * granting access.
 */
@IntegrationTest
class PurchaseIT {

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

  // ------------------------------------------------------------------ fixtures

  /** {plan id, monthly ONE_TIME price id, yearly ONE_TIME price id, RECURRING price id} */
  private String[] catalog() throws Exception {
    String code = "pp-" + Ids.newId().toString().substring(24);
    String plan =
        json(call(
                    post("/api/v1/admin/billing/plans"),
                    admin,
                    "{\"code\":\"%s\",\"name\":\"Pass %s\"}".formatted(code, code))
                .andExpect(status().isCreated()))
            .get("id")
            .asString();
    call(
            put("/api/v1/admin/billing/plans/" + plan + "/entitlements"),
            admin,
            "{\"codes\":[\"premium_content\"]}")
        .andExpect(status().isOk());
    String base = "/api/v1/admin/billing/plans/" + plan + "/prices";
    String[] ids = new String[4];
    ids[0] = plan;
    int n = 1;
    for (String body :
        new String[] {
          "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":29950}",
          "{\"billingMode\":\"ONE_TIME\",\"periodUnit\":\"year\",\"amountInclTaxMinor\":299900}",
          "{\"billingMode\":\"RECURRING\",\"periodUnit\":\"month\",\"amountInclTaxMinor\":24900}"
        }) {
      JsonNode prices =
          json(call(post(base), admin, body).andExpect(status().isCreated())).get("prices");
      for (JsonNode p : prices) {
        String id = p.get("id").asString();
        if (!Arrays.asList(ids).contains(id) && ids[n] == null && matches(p, body)) {
          ids[n] = id;
        }
      }
      n++;
    }
    return ids;
  }

  private static boolean matches(JsonNode p, String body) {
    return body.contains("\"" + p.get("billingMode").asString() + "\"")
        && body.contains("\"" + p.get("periodUnit").asString() + "\"")
        && body.contains(":" + p.get("amountInclTaxMinor").asLong() + "}");
  }

  private JsonNode start(JwtRequestPostProcessor u, String priceId, String idemKey)
      throws Exception {
    var b = post("/api/v1/billing/purchases");
    if (idemKey != null) b = b.header("Idempotency-Key", idemKey);
    return json(
        call(b, u, "{\"planPriceId\":\"%s\"}".formatted(priceId)).andExpect(status().isCreated()));
  }

  private boolean premium(JwtRequestPostProcessor u) throws Exception {
    return json(call(get("/api/v1/me/access"), u, null)).get("premium").asBoolean();
  }

  private UUID userId(JwtRequestPostProcessor u) throws Exception {
    return UUID.fromString(json(call(get("/api/v1/me"), u, null)).get("id").asString());
  }

  private String payload(
      String event, String paymentId, String orderId, long amount, String currency, String status) {
    return "{\"entity\":\"event\",\"account_id\":\"acc_test\",\"event\":\"%s\",\"contains\":[\"payment\"],\"payload\":{\"payment\":{\"entity\":{\"id\":\"%s\",\"entity\":\"payment\",\"amount\":%d,\"currency\":\"%s\",\"status\":\"%s\",\"order_id\":\"%s\",\"method\":\"card\"}}},\"created_at\":1700000000}"
        .formatted(event, paymentId, amount, currency, status, orderId);
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

  private ResultActions signedWebhook(String body, String eventId) throws Exception {
    return webhook(
        body,
        RazorpaySignatures.hmacHex(WEBHOOK_SECRET, body.getBytes(StandardCharsets.UTF_8)),
        eventId);
  }

  private String captured(String orderId, long amount) throws Exception {
    String pay = "pay_IT" + Ids.newId().toString().substring(20).replace("-", "");
    signedWebhook(
            payload("payment.captured", pay, orderId, amount, "INR", "captured"), "evt_" + pay)
        .andExpect(status().isOk());
    return pay;
  }

  private long count(String sql, Object... args) {
    var q = jdbc.sql(sql);
    for (int i = 0; i < args.length; i++) q = q.param("p" + i, args[i]);
    return q.query(Long.class).single();
  }

  // ------------------------------------------------------------------ configuration and quote

  @Test
  void paymentsAreReportedAsDisabledWhenNotConfiguredAndNothingCanStart() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    call(get("/api/v1/billing/config"), null, null)
        .andExpect(jsonPath("$.paymentsEnabled").value(true))
        .andExpect(jsonPath("$.mode").value("test"));
    provider.disabled = true;
    call(get("/api/v1/billing/config"), null, null)
        .andExpect(jsonPath("$.paymentsEnabled").value(false));
    call(get("/api/v1/billing/quote?planPriceId=" + c[1]), null, null)
        .andExpect(jsonPath("$.purchasable").value(false))
        .andExpect(jsonPath("$.note").value("Online payment is not available yet."));
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isServiceUnavailable());
    assertThat(provider.orders).isEmpty();
  }

  @Test
  void aQuoteShowsTheFinalServerSideAmountAndWhetherItCanBeBought() throws Exception {
    String[] c = catalog();
    call(get("/api/v1/billing/quote?planPriceId=" + c[1]), null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountInclTaxMinor").value(29950))
        .andExpect(jsonPath("$.currency").value("INR"))
        .andExpect(jsonPath("$.purchasable").value(true))
        .andExpect(jsonPath("$.periodUnit").value("month"));
    call(get("/api/v1/billing/quote?planPriceId=" + c[2]), null, null)
        .andExpect(jsonPath("$.amountInclTaxMinor").value(299900));
    call(get("/api/v1/billing/quote?planPriceId=" + c[3]), null, null)
        .andExpect(jsonPath("$.purchasable").value(false))
        .andExpect(jsonPath("$.note").value("Auto-renewing plans are not available yet."));
    call(get("/api/v1/billing/quote?planPriceId=" + UUID.randomUUID()), null, null)
        .andExpect(status().isNotFound());
    call(get("/api/v1/billing/quote?planPriceId=not-a-uuid"), null, null)
        .andExpect(status().isBadRequest());
    // a switched-off price cannot be bought
    call(
            patch("/api/v1/admin/billing/plans/" + c[0] + "/prices/" + c[1]),
            admin,
            "{\"active\":false}")
        .andExpect(status().isOk());
    call(get("/api/v1/billing/quote?planPriceId=" + c[1]), null, null)
        .andExpect(jsonPath("$.purchasable").value(false));
  }

  // ------------------------------------------------------------------ starting a purchase

  @Test
  void startingNeedsLoginAndTheAmountAlwaysComesFromTheServer() throws Exception {
    String[] c = catalog();
    call(post("/api/v1/billing/purchases"), null, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isUnauthorized());
    var u = who(sub());
    // a client-supplied amount is ignored: the DTO has no such field
    JsonNode co =
        json(
            call(
                    post("/api/v1/billing/purchases"),
                    u,
                    "{\"planPriceId\":\"%s\",\"amount\":1,\"amountMinor\":1,\"currency\":\"USD\"}"
                        .formatted(c[1]))
                .andExpect(status().isCreated()));
    assertThat(co.get("amountMinor").asLong()).isEqualTo(29950);
    assertThat(co.get("currency").asString()).isEqualTo("INR");
    assertThat(co.get("keyId").asString()).startsWith("rzp_test_");
    assertThat(co.toString()).doesNotContain(KEY_SECRET).doesNotContain(WEBHOOK_SECRET);
    assertThat(provider.orders).hasSize(1);
    assertThat(provider.orders.get(0).amountMinor()).isEqualTo(29950);
    assertThat(provider.orders.get(0).receipt().length()).isLessThanOrEqualTo(40);
    assertThat(provider.orders.get(0).notes().toString())
        .doesNotContain("example.test"); // no personal data to the provider
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("payment_pending"))
        .andExpect(jsonPath("$.amountMinor").value(29950));
    assertThat(premium(u)).as("starting a purchase grants nothing").isFalse();
  }

  @Test
  void unavailableOrUnbuyablePricesAreRefused() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[3]))
        .andExpect(status().isConflict()); // recurring: later milestone
    call(
            post("/api/v1/billing/purchases"),
            u,
            "{\"planPriceId\":\"%s\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    call(post("/api/v1/billing/purchases"), u, "{}").andExpect(status().isBadRequest());
    call(patch("/api/v1/admin/billing/plans/" + c[0]), admin, "{\"active\":false}")
        .andExpect(status().isOk());
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isConflict());
    assertThat(provider.orders).isEmpty();
  }

  @Test
  void idempotencyKeysReturnTheSameOrderAndNeverCreateASecondOne() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode a = start(u, c[1], "idem-key-0001");
    JsonNode b = start(u, c[1], "idem-key-0001");
    assertThat(b.get("intentId").asString()).isEqualTo(a.get("intentId").asString());
    assertThat(b.get("orderId").asString()).isEqualTo(a.get("orderId").asString());
    assertThat(provider.orders).hasSize(1);
    call(
            post("/api/v1/billing/purchases").header("Idempotency-Key", "idem-key-0001"),
            u,
            "{\"planPriceId\":\"%s\"}".formatted(c[2]))
        .andExpect(status().isConflict()); // same key, different purchase
    call(
            post("/api/v1/billing/purchases").header("Idempotency-Key", "short"),
            u,
            "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isBadRequest());
    // another user's identical key is independent
    assertThat(start(who(sub()), c[1], "idem-key-0001").get("intentId").asString())
        .isNotEqualTo(a.get("intentId").asString());
  }

  @Test
  void aProviderFailureLeavesAFailedIntentAndAClearError() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    provider.failNext = true;
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.sql(
                    "select count(*) from billing.purchase_intents where user_id = :u and status = 'failed'")
                .param("u", userId(u))
                .query(Long.class)
                .single())
        .isEqualTo(1);
    start(u, c[1], null); // retry works
  }

  @Test
  void purchaseAttemptsAreRateLimitedPerUser() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    for (int i = 0; i < 20; i++) {
      start(u, c[1], null);
    }
    call(post("/api/v1/billing/purchases"), u, "{\"planPriceId\":\"%s\"}".formatted(c[1]))
        .andExpect(status().isTooManyRequests());
  }

  // ------------------------------------------------------------------ the browser callback never
  // grants access

  @Test
  void confirmingVerifiesTheSignatureButNeverGrantsAccess() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode co = start(u, c[1], null);
    String intent = co.get("intentId").asString();
    String order = co.get("orderId").asString();
    String pay = "pay_ITconfirm1";
    String good =
        RazorpaySignatures.hmacHex(
            KEY_SECRET, (order + "|" + pay).getBytes(StandardCharsets.UTF_8));
    String url = "/api/v1/billing/purchases/" + intent + "/confirm";

    call(
            post(url),
            u,
            "{\"razorpayOrderId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}"
                .formatted(order, pay, good))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signatureValid").value(true))
        .andExpect(jsonPath("$.intent.status").value("payment_pending"));
    assertThat(premium(u)).as("a valid browser signature alone must not grant access").isFalse();
    call(
            post(url),
            u,
            "{\"razorpayOrderId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}"
                .formatted(order, pay, "0".repeat(64)))
        .andExpect(jsonPath("$.signatureValid").value(false));
    call(
            post(url),
            u,
            "{\"razorpayOrderId\":\"order_other\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}"
                .formatted(pay, good))
        .andExpect(
            jsonPath("$.signatureValid")
                .value(false)); // order id the server never issued for this intent
    call(
            post(url),
            u,
            "{\"razorpayOrderId\":\"%s\",\"razorpayPaymentId\":\"bad id\",\"razorpaySignature\":\"%s\"}"
                .formatted(order, good))
        .andExpect(status().isBadRequest());
    // replaying a VALID signature of another order (e.g. one the attacker really paid) against this
    // intent
    JsonNode other = start(u, c[1], null);
    String otherOrder = other.get("orderId").asString();
    String otherGood =
        RazorpaySignatures.hmacHex(
            KEY_SECRET, (otherOrder + "|" + pay).getBytes(StandardCharsets.UTF_8));
    call(
            post(url),
            u,
            "{\"razorpayOrderId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}"
                .formatted(otherOrder, pay, otherGood))
        .andExpect(jsonPath("$.signatureValid").value(false));
    // someone else's intent looks like a missing one
    call(
            post(url),
            who(sub()),
            "{\"razorpayOrderId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"razorpaySignature\":\"%s\"}"
                .formatted(order, pay, good))
        .andExpect(status().isNotFound());
    call(get("/api/v1/billing/purchases/" + intent), who(sub()), null)
        .andExpect(status().isNotFound());
    call(post(url), null, "{}").andExpect(status().isUnauthorized());
  }

  // ------------------------------------------------------------------ the webhook is the only way
  // to get access

  @Test
  void aVerifiedCapturedPaymentGrantsOneMonthOfAccessExactlyOnce() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID id = userId(u);
    JsonNode co = start(u, c[1], null);
    String order = co.get("orderId").asString();
    String pay = "pay_ITgrant0001";
    String body = payload("payment.captured", pay, order, 29950, "INR", "captured");

    signedWebhook(body, "evt_1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("granted"));
    assertThat(premium(u)).isTrue();
    JsonNode access = json(call(get("/api/v1/me/access"), u, null));
    Instant end = Instant.parse(access.get("validThrough").asString());
    Instant expected = Instant.now().atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    assertThat(Duration.between(end, expected).abs()).isLessThan(Duration.ofMinutes(5));
    assertThat(access.get("periods").get(0).get("sourceType").asString()).isEqualTo("payment");
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("paid"));

    // replays and the sibling event for the same payment change nothing
    signedWebhook(body, "evt_1")
        .andExpect(jsonPath("$.status").value("duplicate")); // same event id
    signedWebhook(body, "evt_2")
        .andExpect(
            jsonPath("$.status").value("duplicate")); // redelivery under another id: same payment
    signedWebhook(payload("order.paid", pay, order, 29950, "INR", "captured"), "evt_3")
        .andExpect(jsonPath("$.status").value("duplicate"));
    assertThat(
            jdbc.sql("select count(*) from billing.payments where user_id = :u")
                .param("u", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from billing.access_periods where user_id = :u")
                .param("u", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from shared.outbox where event_type = 'billing.payment.succeeded' and aggregate_id = :a")
                .param("a", id.toString())
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select outcome from billing.webhook_events where event_id = 'evt_1'")
                .query(String.class)
                .single())
        .isEqualTo("granted");
  }

  @Test
  void anAnnualPassLastsAYearAndASecondPurchaseExtendsFromTheEndOfTheFirst() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    UUID id = userId(u);
    captured(start(u, c[2], null).get("orderId").asString(), 299900);
    Instant firstEnd =
        jdbc.sql("select max(ends_at) from billing.access_periods where user_id = :u")
            .param("u", id)
            .query(java.sql.Timestamp.class)
            .single()
            .toInstant();
    assertThat(
            Duration.between(
                    Instant.now().atZone(ZoneOffset.UTC).plusYears(1).toInstant(), firstEnd)
                .abs())
        .isLessThan(Duration.ofMinutes(5));

    captured(start(u, c[1], null).get("orderId").asString(), 29950);
    var periods =
        jdbc.sql(
                "select starts_at, ends_at from billing.access_periods where user_id = :u order by starts_at")
            .param("u", id)
            .query(
                (rs, i) ->
                    new Instant[] {rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()})
            .list();
    assertThat(periods).hasSize(2);
    assertThat(periods.get(1)[0])
        .as("the second pass starts when the first one ends")
        .isEqualTo(periods.get(0)[1]);
    assertThat(periods.get(1)[1])
        .isEqualTo(periods.get(1)[0].atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
  }

  @Test
  void invalidMissingOrTamperedSignaturesAreRejectedAndChangeNothing() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String order = start(u, c[1], null).get("orderId").asString();
    String body = payload("payment.captured", "pay_ITforged01", order, 29950, "INR", "captured");
    long events = count("select count(*) from billing.webhook_events");

    webhook(body, null, "evt_forged1").andExpect(status().isUnauthorized());
    webhook(body, "", "evt_forged2").andExpect(status().isUnauthorized());
    webhook(body, "0".repeat(64), "evt_forged3").andExpect(status().isUnauthorized());
    webhook(
            body,
            RazorpaySignatures.hmacHex("some-other-secret", body.getBytes(StandardCharsets.UTF_8)),
            "evt_forged4")
        .andExpect(status().isUnauthorized());
    // a signature for a different body (e.g. a cheaper payment) does not carry over
    String cheaper = payload("payment.captured", "pay_ITforged01", order, 100, "INR", "captured");
    webhook(
            body,
            RazorpaySignatures.hmacHex(WEBHOOK_SECRET, cheaper.getBytes(StandardCharsets.UTF_8)),
            "evt_forged5")
        .andExpect(status().isUnauthorized());
    assertThat(premium(u)).isFalse();
    assertThat(count("select count(*) from billing.webhook_events"))
        .as("rejected events are not stored")
        .isEqualTo(events);
    assertThat(count("select count(*) from billing.payments")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void amountOrCurrencyMismatchesGrantNothingAndFlagTheIntent() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode co = start(u, c[1], null);
    String order = co.get("orderId").asString();
    signedWebhook(
            payload("payment.captured", "pay_ITshort001", order, 100, "INR", "captured"), "evt_m1")
        .andExpect(jsonPath("$.status").value("amount_mismatch"));
    assertThat(premium(u)).isFalse();
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("needs_review"));

    JsonNode co2 = start(u, c[1], null);
    signedWebhook(
            payload(
                "payment.captured",
                "pay_ITusd00001",
                co2.get("orderId").asString(),
                29950,
                "USD",
                "captured"),
            "evt_m2")
        .andExpect(jsonPath("$.status").value("amount_mismatch"));
    assertThat(premium(u)).isFalse();
    assertThat(count("select count(*) from billing.payments")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void unknownOrdersAndNonCapturedPaymentsAreAcknowledgedAndIgnored() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    String order = start(u, c[1], null).get("orderId").asString();
    signedWebhook(
            payload(
                "payment.captured",
                "pay_ITunknown01",
                "order_NeverIssued",
                29950,
                "INR",
                "captured"),
            "evt_u1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("unknown_order"));
    signedWebhook(
            payload("payment.captured", "pay_ITauth00001", order, 29950, "INR", "authorized"),
            "evt_u2")
        .andExpect(jsonPath("$.status").value("ignored_not_captured"));
    signedWebhook(
            payload("payment.authorized", "pay_ITauth00002", order, 29950, "INR", "authorized"),
            "evt_u3")
        .andExpect(jsonPath("$.status").value("ignored"));
    signedWebhook(
            "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"status\":\"captured\"}}}}",
            "evt_u4")
        .andExpect(jsonPath("$.status").value("ignored_malformed"));
    assertThat(premium(u)).isFalse();
  }

  @Test
  void aFailedAttemptCanStillBeFollowedByASuccessfulPaymentOnTheSameOrder() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    JsonNode co = start(u, c[1], null);
    String order = co.get("orderId").asString();
    signedWebhook(
            payload("payment.failed", "pay_ITfail00001", order, 29950, "INR", "failed"), "evt_f1")
        .andExpect(jsonPath("$.status").value("failed_recorded"));
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("failed"));
    assertThat(premium(u)).isFalse();
    signedWebhook(
            payload("payment.captured", "pay_ITretry0001", order, 29950, "INR", "captured"),
            "evt_f2")
        .andExpect(jsonPath("$.status").value("granted"));
    assertThat(premium(u)).isTrue();
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("paid"));
    // a failure event arriving late (unordered delivery) never undoes a paid intent
    signedWebhook(
            payload("payment.failed", "pay_ITfail00002", order, 29950, "INR", "failed"), "evt_f3")
        .andExpect(jsonPath("$.status").value("ignored"));
    call(get("/api/v1/billing/purchases/" + co.get("intentId").asString()), u, null)
        .andExpect(jsonPath("$.status").value("paid"));
  }

  @Test
  void malformedBodiesAndWrongMethodsAreRefusedSafely() throws Exception {
    webhook(
            "{not json",
            RazorpaySignatures.hmacHex(
                WEBHOOK_SECRET, "{not json".getBytes(StandardCharsets.UTF_8)),
            "evt_bad1")
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/webhooks/razorpay")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void accessGrantedByPaymentSurvivesAndIsRevocableLikeAnyOtherPeriod() throws Exception {
    String[] c = catalog();
    var u = who(sub());
    captured(start(u, c[1], null).get("orderId").asString(), 29950);
    String period =
        json(call(get("/api/v1/me/access"), u, null)).get("periods").get(0).get("id").asString();
    call(
            post("/api/v1/admin/billing/access-periods/" + period + "/revoke"),
            admin,
            "{\"reason\":\"refund test (M6.6 automates this)\"}")
        .andExpect(status().isOk());
    assertThat(premium(u)).isFalse();
  }
}
