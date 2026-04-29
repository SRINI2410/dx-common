package org.cdpg.dx.auth.v2.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Objects;
import org.cdpg.dx.auth.v2.resolver.AppCredentialsResolver;
import org.cdpg.dx.auth.v2.resolver.DelegationResolver;
import org.cdpg.dx.auth.v2.resolver.JwtPrincipalResolver;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

/**
 * Top-level authentication entry point. Inspects request headers and dispatches to exactly one of
 * the three principal resolvers. On success, the downstream handler sees a {@link
 * org.cdpg.dx.auth.v2.model.DxPrincipal} at {@link AuthorizationHandler#PRINCIPAL_KEY}.
 *
 * <p>Dispatch rules, evaluated in order:
 *
 * <ol>
 *   <li>Both an app credential header AND an {@code Authorization} header → 400 (ambiguous).
 *   <li>Any app-credential header present → {@link AppCredentialsResolver}.
 *   <li>{@code Authorization: Bearer …} + {@code X-Delegator-Id} → {@link DelegationResolver}.
 *   <li>{@code Authorization: Bearer …} alone → {@link JwtPrincipalResolver}.
 *   <li>Otherwise → 401.
 * </ol>
 *
 * <p><b>App-credential headers:</b> accepted as either {@code X-App-Id} + {@code X-App-Secret}, or
 * {@code Authorization: Basic base64(appId:secret)}. The Basic-auth form is treated as an
 * app-credential header, not a JWT header — i.e. it never matches the JWT/delegation branches.
 *
 * <p>This handler does NOT validate JWT signatures. An upstream JWT auth handler (e.g.
 * {@code MultiIssuerJwtAuthHandler}) must run first for the JWT / delegation paths to populate
 * {@code ctx.user()}.
 */
public final class AuthenticationHandler implements Handler<RoutingContext> {

  private final JwtPrincipalResolver jwtResolver;
  private final DelegationResolver delegationResolver;
  private final AppCredentialsResolver appResolver;

  public AuthenticationHandler(
      JwtPrincipalResolver jwtResolver,
      DelegationResolver delegationResolver,
      AppCredentialsResolver appResolver) {
    this.jwtResolver = Objects.requireNonNull(jwtResolver, "jwtResolver");
    this.delegationResolver = Objects.requireNonNull(delegationResolver, "delegationResolver");
    this.appResolver = Objects.requireNonNull(appResolver, "appResolver");
  }

  @Override
  public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    String appIdHeader = ctx.request().getHeader("X-App-Id");
    String delegatorHeader = ctx.request().getHeader("X-Delegator-Id");

    boolean hasBearer = authHeader != null && authHeader.startsWith("Bearer ");
    boolean hasBasic = authHeader != null && authHeader.startsWith("Basic ");
    boolean hasAppHeader = appIdHeader != null && !appIdHeader.isBlank();

    // App path is signalled by EITHER X-App-Id or Basic auth. Mixing app credentials with a Bearer
    // JWT in the same request is always ambiguous.
    boolean appPath = hasAppHeader || hasBasic;
    if (appPath && hasBearer) {
      ctx.fail(
          new DxBadRequestException(
              "Ambiguous credentials: send either JWT or app credentials, not both"));
      return;
    }

    if (appPath) {
      appResolver.resolve(ctx);
      return;
    }

    if (hasBearer) {
      if (delegatorHeader != null && !delegatorHeader.isBlank()) {
        delegationResolver.resolve(ctx);
      } else {
        jwtResolver.resolve(ctx);
      }
      return;
    }

    ctx.fail(new DxUnauthorizedException("Missing credentials"));
  }
}