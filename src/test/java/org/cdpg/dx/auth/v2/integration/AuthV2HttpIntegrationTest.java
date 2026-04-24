package org.cdpg.dx.auth.v2.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.lookup.AppCredentialLookup;
import org.cdpg.dx.auth.v2.lookup.DelegationLookup;
import org.cdpg.dx.auth.v2.lookup.UserLookup;
import org.cdpg.dx.auth.v2.model.AppPrincipal;
import org.cdpg.dx.auth.v2.model.DelegationRecord;
import org.cdpg.dx.auth.v2.model.DxPrincipal;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.auth.v2.model.Scopes;
import org.cdpg.dx.auth.v2.model.UserSnapshot;
import org.cdpg.dx.auth.v2.registry.InMemoryRoleScopeRegistry;
import org.cdpg.dx.auth.v2.resolver.AppCredentialsResolver;
import org.cdpg.dx.auth.v2.resolver.DelegationResolver;
import org.cdpg.dx.auth.v2.resolver.JwtPrincipalResolver;
import org.cdpg.dx.common.FailureHandler;
import org.cdpg.dx.common.URNGenerator;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * End-to-end smoke test for the v2 auth stack. Mounts the full handler chain
 * (AuthenticationHandler → AuthorizationHandler.forScopes → echo route) on a real Vert.x HTTP
 * server backed by deterministic fake lookups, and fires real HTTP requests to verify header
 * dispatch, scope capping, and error-code translation.
 *
 * <p>The JWT-validation step is stubbed via a test-only bridge handler that reads an {@code
 * X-Test-Claims} header (JSON) and calls {@code ctx.setUser(User.create(json))}, simulating what
 * {@code MultiIssuerJwtAuthHandler} would do in production. Tests that exercise JWT / delegation
 * paths send a dummy {@code Authorization: Bearer …} alongside the claims header.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV2HttpIntegrationTest {

  private static final String APP_ID = "11111111-1111-1111-1111-111111111111";
  private static final String APP_SECRET = "secret-123";
  private static final String OWNER_SUB = "22222222-2222-2222-2222-222222222222";
  private static final String OWNER_ORG = "org-owner";

  private static final String ALICE_SUB = "33333333-3333-3333-3333-333333333333";
  private static final String ALICE_ORG = "org-a";
  private static final String BOB_SUB = "44444444-4444-4444-4444-444444444444";
  private static final String BOB_ORG = "org-b";

  private HttpServer server;
  private WebClient client;
  private int port;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) {
    AppCredentialLookup apps =
        (id, secret) -> {
          if (APP_ID.equals(id) && APP_SECRET.equals(secret)) {
            return Future.succeededFuture(
                Optional.of(
                    new AppPrincipal(
                        APP_ID,
                        OWNER_SUB,
                        OWNER_ORG,
                        List.of(Scopes.OWN_ASSET_MANAGEMENT),
                        0L,
                        true)));
          }
          return Future.succeededFuture(Optional.empty());
        };

    DelegationLookup delegations =
        (delegator, delegatee) -> {
          if (ALICE_SUB.equals(delegator) && BOB_SUB.equals(delegatee)) {
            return Future.succeededFuture(
                Optional.of(
                    new DelegationRecord(
                        ALICE_SUB, BOB_SUB, Set.of(Scopes.DATA_ACCESS), false, true, 0L)));
          }
          return Future.succeededFuture(Optional.empty());
        };

    Map<String, UserSnapshot> users =
        Map.of(
            OWNER_SUB, new UserSnapshot(OWNER_SUB, OWNER_ORG, Set.of(DxRole.PROVIDER), false),
            ALICE_SUB, new UserSnapshot(ALICE_SUB, ALICE_ORG, Set.of(DxRole.CONSUMER), false),
            BOB_SUB, new UserSnapshot(BOB_SUB, BOB_ORG, Set.of(DxRole.PROVIDER), false));
    UserLookup usersLookup =
        sub -> Future.succeededFuture(Optional.ofNullable(users.get(sub)));

    AuthenticationHandler authN =
        new AuthenticationHandler(
            new JwtPrincipalResolver(),
            new DelegationResolver(delegations, usersLookup),
            new AppCredentialsResolver(apps, usersLookup));
    AuthorizationHandler authZ = new AuthorizationHandler(new InMemoryRoleScopeRegistry());

    Router router = Router.router(vertx);

    router
        .route()
        .handler(
            rc -> {
              String testClaims = rc.request().getHeader("X-Test-Claims");
              if (testClaims != null && !testClaims.isBlank()) {
                rc.setUser(User.create(new JsonObject(testClaims)));
              }
              rc.next();
            });

    router
        .route("/assets")
        .handler(authN)
        .handler(authZ.forScopes(Scopes.OWN_ASSET_MANAGEMENT))
        .handler(this::echoPrincipal);

    router
        .route("/read")
        .handler(authN)
        .handler(authZ.forScopes(Scopes.DATA_ACCESS))
        .handler(this::echoPrincipal);

    router.route().failureHandler(new FailureHandler(new URNGenerator("urn:dx:test:")));

    server = vertx.createHttpServer();
    server
        .requestHandler(router)
        .listen(0)
        .onSuccess(
            httpServer -> {
              port = httpServer.actualPort();
              client = WebClient.create(vertx);
              ctx.completeNow();
            })
        .onFailure(ctx::failNow);
  }

  @AfterAll
  void tearDown(VertxTestContext ctx) {
    client.close();
    server.close().onComplete(v -> ctx.completeNow());
  }

  private void echoPrincipal(io.vertx.ext.web.RoutingContext rc) {
    DxPrincipal p = rc.get(AuthorizationHandler.PRINCIPAL_KEY);
    JsonObject body =
        new JsonObject()
            .put("sub", p.getSub())
            .put("organisationId", p.getOrganisationId())
            .put("authenticatedSub", p.getAuthenticatedSub())
            .put("isApp", p.isApp())
            .put("isDelegation", p.isDelegation())
            .put("appId", p.getAppId());
    rc.response()
        .putHeader("content-type", "application/json")
        .setStatusCode(200)
        .end(body.encode());
  }

  // ─── Helpers ──────────────────────────────────────────────────────────

  private static String jwtClaims(String sub, String orgId, String... roles) {
    JsonArray rolesArr = new JsonArray();
    for (String r : roles) rolesArr.add(r);
    return new JsonObject()
        .put("sub", sub)
        .put("organisation_id", orgId)
        .put("realm_access", new JsonObject().put("roles", rolesArr))
        .encode();
  }

  private Future<HttpResponse<io.vertx.core.buffer.Buffer>> get(
      String path, Map<String, String> headers) {
    var req = client.get(port, "localhost", path);
    headers.forEach(req::putHeader);
    return req.send();
  }

  // ─── Tests ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Plain JWT — PROVIDER can hit /assets (has own-asset-management)")
  void plainJwt_provider_canAccessAssets(VertxTestContext ctx) throws Exception {
    get(
            "/assets",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(OWNER_SUB, OWNER_ORG, "provider")))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(200);
                          JsonObject body = res.bodyAsJsonObject();
                          assertThat(body.getString("sub")).isEqualTo(OWNER_SUB);
                          assertThat(body.getBoolean("isApp")).isFalse();
                          assertThat(body.getBoolean("isDelegation")).isFalse();
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("Plain JWT — CONSUMER cannot hit /assets (lacks own-asset-management) → 403")
  void plainJwt_consumer_deniedAssets(VertxTestContext ctx) throws Exception {
    get(
            "/assets",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(ALICE_SUB, ALICE_ORG, "consumer")))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(403);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("Plain JWT — CONSUMER can hit /read (has data-access)")
  void plainJwt_consumer_canRead(VertxTestContext ctx) throws Exception {
    get(
            "/read",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(ALICE_SUB, ALICE_ORG, "consumer")))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(200);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("App creds — valid → 200, sub/org resolve to owner, appId populated")
  void appCreds_happyPath(VertxTestContext ctx) throws Exception {
    get(
            "/assets",
            Map.of(
                "X-App-Id", APP_ID,
                "X-App-Secret", APP_SECRET))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(200);
                          JsonObject body = res.bodyAsJsonObject();
                          assertThat(body.getString("sub")).isEqualTo(OWNER_SUB);
                          assertThat(body.getString("organisationId")).isEqualTo(OWNER_ORG);
                          assertThat(body.getString("appId")).isEqualTo(APP_ID);
                          assertThat(body.getBoolean("isApp")).isTrue();
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("App creds — wrong secret → 401")
  void appCreds_wrongSecret(VertxTestContext ctx) throws Exception {
    get(
            "/assets",
            Map.of(
                "X-App-Id", APP_ID,
                "X-App-Secret", "nope"))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(401);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("Delegation — Bob-as-Alice with data-access → /read returns 200 as alice")
  void delegation_happyPath(VertxTestContext ctx) throws Exception {
    get(
            "/read",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(BOB_SUB, BOB_ORG, "provider"),
                "X-Delegator-Id", ALICE_SUB))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(200);
                          JsonObject body = res.bodyAsJsonObject();
                          assertThat(body.getString("sub"))
                              .as("effective sub is delegator alice")
                              .isEqualTo(ALICE_SUB);
                          assertThat(body.getString("authenticatedSub"))
                              .as("raw authenticated sub is delegatee bob")
                              .isEqualTo(BOB_SUB);
                          assertThat(body.getBoolean("isDelegation")).isTrue();
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("Delegation — no matching delegation → 403")
  void delegation_noMatch(VertxTestContext ctx) throws Exception {
    get(
            "/read",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(BOB_SUB, BOB_ORG, "provider"),
                "X-Delegator-Id", "nobody"))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(403);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("Ambiguous — Bearer + X-App-Id → 400")
  void ambiguous(VertxTestContext ctx) throws Exception {
    get(
            "/assets",
            Map.of(
                "Authorization", "Bearer dummy",
                "X-Test-Claims", jwtClaims(OWNER_SUB, OWNER_ORG, "provider"),
                "X-App-Id", APP_ID,
                "X-App-Secret", APP_SECRET))
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(400);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("No credentials → 401")
  void noCredentials(VertxTestContext ctx) throws Exception {
    get("/assets", Map.of())
        .onComplete(
            ctx.succeeding(
                res ->
                    ctx.verify(
                        () -> {
                          assertThat(res.statusCode()).isEqualTo(401);
                          ctx.completeNow();
                        })));
    assertThat(ctx.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }
}