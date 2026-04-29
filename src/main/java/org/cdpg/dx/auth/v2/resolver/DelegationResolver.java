package org.cdpg.dx.auth.v2.resolver;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.lookup.DelegationLookup;
import org.cdpg.dx.auth.v2.lookup.UserLookup;
import org.cdpg.dx.auth.v2.model.DelegationRecord;
import org.cdpg.dx.auth.v2.model.DxPrincipal;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.auth.v2.model.UserSnapshot;
import org.cdpg.dx.auth.v2.registry.SystemRoleScopeMap;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

/**
 * Resolves a JWT + {@code X-Delegator-Id} header into a {@link DxPrincipal} acting <em>as</em> the
 * delegator. The JWT must already be validated by an upstream auth handler.
 *
 * <p>The JWT must include the {@code sub} claim. The {@code organisation_id} claim is optional.
 *
 * <p>Effective scopes are the intersection of the delegation's stored scopes (or the delegator's
 * current flattened scopes, for a "full" delegation) and the delegator's current role-derived
 * scopes — so losing a role on the delegator immediately caps the delegation.
 */
public final class DelegationResolver {

  private final DelegationLookup delegationLookup;
  private final UserLookup userLookup;

  public DelegationResolver(DelegationLookup delegationLookup, UserLookup userLookup) {
    this.delegationLookup = Objects.requireNonNull(delegationLookup, "delegationLookup");
    this.userLookup = Objects.requireNonNull(userLookup, "userLookup");
  }

  public void resolve(RoutingContext ctx) {
    User user = ctx.user();
    if (user == null) {
      ctx.fail(new DxUnauthorizedException("Missing JWT"));
      return;
    }
    JsonObject claims = user.principal();
    String delegateeSub = claims.getString("sub");
    String delegateeOrgId = claims.getString("organisation_id");
    if (delegateeSub == null) {
      ctx.fail(new DxUnauthorizedException("JWT missing sub"));
      return;
    }

    String delegatorSub = ctx.request().getHeader("X-Delegator-Id");
    if (delegatorSub == null || delegatorSub.isBlank()) {
      ctx.fail(new DxUnauthorizedException("Missing X-Delegator-Id header"));
      return;
    }

    delegationLookup
        .findActive(delegatorSub, delegateeSub)
        .onFailure(err -> ctx.fail(new DxUnauthorizedException("Delegation lookup failed")))
        .onSuccess(
            maybeDelegation -> {
              if (maybeDelegation.isEmpty() || !maybeDelegation.get().active()) {
                ctx.fail(new DxForbiddenException("No active delegation from " + delegatorSub));
                return;
              }
              DelegationRecord delegation = maybeDelegation.get();
              if (isExpired(delegation)) {
                ctx.fail(new DxForbiddenException("Delegation has expired"));
                return;
              }

              userLookup
                  .findBySub(delegatorSub)
                  .onFailure(err -> ctx.fail(new DxUnauthorizedException("User lookup failed")))
                  .onSuccess(
                      maybeDelegator -> {
                        if (maybeDelegator.isEmpty() || maybeDelegator.get().disabled()) {
                          ctx.fail(new DxForbiddenException("Delegator is no longer active"));
                          return;
                        }
                        UserSnapshot delegator = maybeDelegator.get();
                        DxPrincipal principal =
                            buildPrincipal(delegateeSub, delegateeOrgId, delegator, delegation);
                        ctx.put(AuthorizationHandler.PRINCIPAL_KEY, principal);
                        ctx.next();
                      });
            });
  }

  private DxPrincipal buildPrincipal(
      String delegateeSub,
      String delegateeOrgId,
      UserSnapshot delegator,
      DelegationRecord delegation) {

    Set<String> delegatorCurrentScopes = flatten(delegator.roles());
    Set<String> capped;
    if (delegation.fullDelegation()) {
      capped = delegatorCurrentScopes;
    } else {
      capped = new HashSet<>(delegation.scopes());
      capped.retainAll(delegatorCurrentScopes);
    }

    return DxPrincipal.builder()
        .authenticatedSub(delegateeSub)
        .authenticatedOrgId(delegateeOrgId)
        .delegatorSub(delegator.sub())
        .delegatorOrgId(delegator.organisationId())
        .directScopes(capped)
        .auditRoles(delegator.roles())
        .build();
  }

  private static boolean isExpired(DelegationRecord d) {
    long exp = d.expiresAtEpoch();
    return exp != 0 && exp < Instant.now().getEpochSecond();
  }

  private static Set<String> flatten(Set<DxRole> roles) {
    Set<String> out = new HashSet<>();
    for (DxRole r : roles) out.addAll(SystemRoleScopeMap.getScopes(r));
    return out;
  }
}