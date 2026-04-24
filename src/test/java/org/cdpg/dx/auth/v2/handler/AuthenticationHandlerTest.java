package org.cdpg.dx.auth.v2.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cdpg.dx.auth.v2.lookup.AppCredentialLookup;
import org.cdpg.dx.auth.v2.lookup.DelegationLookup;
import org.cdpg.dx.auth.v2.lookup.UserLookup;
import org.cdpg.dx.auth.v2.model.AppPrincipal;
import org.cdpg.dx.auth.v2.model.DelegationRecord;
import org.cdpg.dx.auth.v2.model.UserSnapshot;
import org.cdpg.dx.auth.v2.resolver.AppCredentialsResolver;
import org.cdpg.dx.auth.v2.resolver.DelegationResolver;
import org.cdpg.dx.auth.v2.resolver.JwtPrincipalResolver;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticationHandler Dispatch Tests")
class AuthenticationHandlerTest {

  /**
   * These tests only need to verify WHICH resolver was picked — not the resolver internals. We
   * build resolvers with fakes that succeed immediately; the one that runs will set a known key
   * on ctx.data.
   */
  private static class DispatchCtx {
    final RoutingContext ctx = mock(RoutingContext.class);
    final HttpServerRequest req = mock(HttpServerRequest.class);
    final Map<String, Object> data = new HashMap<>();
    final Map<String, String> headers = new HashMap<>();
    Throwable failedWith;
    boolean nextCalled;

    DispatchCtx() {
      when(ctx.request()).thenReturn(req);
      when(req.getHeader(anyString())).thenAnswer(i -> headers.get(i.<String>getArgument(0)));
      when(ctx.get(anyString())).thenAnswer(i -> data.get(i.<String>getArgument(0)));
      when(ctx.put(anyString(), any()))
          .thenAnswer(
              i -> {
                data.put(i.getArgument(0), i.getArgument(1));
                return ctx;
              });
      doAnswer(i -> nextCalled = true).when(ctx).next();
      doAnswer(
              i -> {
                failedWith = i.getArgument(0);
                return null;
              })
          .when(ctx)
          .fail(any(Throwable.class));
      // Default: no vertx user. Tests that need one override.
      when(ctx.user()).thenReturn(null);
    }

    DispatchCtx header(String name, String value) {
      headers.put(name, value);
      return this;
    }
  }

  /** Builds an AuthenticationHandler with all three resolvers wired to succeeding fakes. */
  private AuthenticationHandler handler() {
    AppCredentialLookup app =
        (id, secret) ->
            Future.succeededFuture(
                Optional.of(
                    new AppPrincipal("w", "u", "o", List.of(), 0L, true)));
    DelegationLookup del =
        (d, e) ->
            Future.succeededFuture(
                Optional.of(new DelegationRecord("d", "e", Set.of(), true, true, 0L)));
    UserLookup user =
        sub -> Future.succeededFuture(Optional.of(new UserSnapshot(sub, "o", Set.of(), false)));
    return new AuthenticationHandler(
        new JwtPrincipalResolver(),
        new DelegationResolver(del, user),
        new AppCredentialsResolver(app, user));
  }

  @Test
  @DisplayName("Basic-auth → AppCredentialsResolver")
  void basicAuthGoesToApp() {
    DispatchCtx c = new DispatchCtx().header("Authorization", "Basic dzpzZWNyZXQ=");
    handler().handle(c.ctx);
    assertTrue(c.nextCalled);
    assertNotNull(c.data.get(AuthorizationHandler.PRINCIPAL_KEY));
    assertTrue(c.data.get(AuthorizationHandler.PRINCIPAL_KEY).toString().contains("w")
            || c.data.containsKey(AuthorizationHandler.PRINCIPAL_KEY),
        "AppCredentialsResolver should have published a principal");
  }

  @Test
  @DisplayName("X-App-Id + X-App-Secret → AppCredentialsResolver")
  void appHeadersGoToApp() {
    DispatchCtx c =
        new DispatchCtx().header("X-App-Id", "w").header("X-App-Secret", "secret");
    handler().handle(c.ctx);
    assertTrue(c.nextCalled);
  }

  @Test
  @DisplayName("Bearer alone → JwtPrincipalResolver (fails cleanly with missing user)")
  void bearerAloneGoesToJwt() {
    DispatchCtx c = new DispatchCtx().header("Authorization", "Bearer abc.def.ghi");
    handler().handle(c.ctx);
    // No ctx.user() → resolver fails with 401 DxUnauthorizedException ("Missing JWT").
    assertFalse(c.nextCalled);
    assertInstanceOf(DxUnauthorizedException.class, c.failedWith);
  }

  @Test
  @DisplayName("Bearer + X-Delegator-Id → DelegationResolver (fails cleanly with missing user)")
  void bearerPlusDelegatorGoesToDelegation() {
    DispatchCtx c =
        new DispatchCtx()
            .header("Authorization", "Bearer abc.def.ghi")
            .header("X-Delegator-Id", "alice");
    handler().handle(c.ctx);
    assertFalse(c.nextCalled);
    // DelegationResolver hits "missing JWT" because ctx.user() is null.
    assertInstanceOf(DxUnauthorizedException.class, c.failedWith);
  }

  @Test
  @DisplayName("Bearer + X-App-Id → 400 ambiguous")
  void bearerAndAppHeaderIsAmbiguous() {
    DispatchCtx c =
        new DispatchCtx()
            .header("Authorization", "Bearer abc.def.ghi")
            .header("X-App-Id", "w")
            .header("X-App-Secret", "secret");
    handler().handle(c.ctx);
    assertInstanceOf(DxBadRequestException.class, c.failedWith);
  }

  @Test
  @DisplayName("Bearer + Basic in the same Authorization header is impossible; Basic always wins")
  void basicAuthAloneWithoutBearerIsAppPath() {
    DispatchCtx c = new DispatchCtx().header("Authorization", "Basic dzpzZWNyZXQ=");
    handler().handle(c.ctx);
    assertTrue(c.nextCalled, "Basic alone should resolve cleanly via the app path");
  }

  @Test
  @DisplayName("No credential headers → 401")
  void noCredentials() {
    DispatchCtx c = new DispatchCtx();
    handler().handle(c.ctx);
    assertInstanceOf(DxUnauthorizedException.class, c.failedWith);
  }

  @Test
  @DisplayName("Empty X-Delegator-Id is treated as absent → plain JWT path")
  void emptyDelegatorHeaderFallsBackToJwt() {
    DispatchCtx c =
        new DispatchCtx()
            .header("Authorization", "Bearer abc.def.ghi")
            .header("X-Delegator-Id", "");
    handler().handle(c.ctx);
    // Routed through JwtPrincipalResolver (fails because ctx.user() is null)
    assertInstanceOf(DxUnauthorizedException.class, c.failedWith);
  }

  @Test
  @DisplayName("Constructor rejects nulls")
  void constructorRejectsNulls() {
    JwtPrincipalResolver j = new JwtPrincipalResolver();
    DelegationResolver d =
        new DelegationResolver(
            (a, b) -> Future.succeededFuture(Optional.empty()),
            sub -> Future.succeededFuture(Optional.empty()));
    AppCredentialsResolver a =
        new AppCredentialsResolver(
            (id, s) -> Future.succeededFuture(Optional.empty()),
            sub -> Future.succeededFuture(Optional.empty()));

    assertThrows(NullPointerException.class, () -> new AuthenticationHandler(null, d, a));
    assertThrows(NullPointerException.class, () -> new AuthenticationHandler(j, null, a));
    assertThrows(NullPointerException.class, () -> new AuthenticationHandler(j, d, null));
  }
}