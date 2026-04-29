package org.cdpg.dx.auth.v2.registry;

import java.util.HashSet;
import java.util.Set;
import org.cdpg.dx.auth.v2.model.DxPrincipal;
import org.cdpg.dx.auth.v2.model.DxRole;

/**
 * In-memory implementation backed by {@link SystemRoleScopeMap}. Effective scopes are the union of
 * scopes flattened from {@code authorizationRoles} and the pre-computed {@code directScopes}.
 *
 * <p>For plain users, {@code authorizationRoles} is non-empty and {@code directScopes} is empty.
 * For delegation and apps, {@code authorizationRoles} is empty and {@code directScopes} is the
 * already-capped scope set. One function, correct for all three paths.
 */
public final class InMemoryRoleScopeRegistry implements RoleScopeRegistry {

  @Override
  public Set<String> resolveEffectiveScopes(DxPrincipal principal) {
    Set<String> effective = new HashSet<>();
    for (DxRole role : principal.getAuthorizationRoles()) {
      effective.addAll(SystemRoleScopeMap.getScopes(role));
    }
    effective.addAll(principal.getDirectScopes());
    return effective;
  }
}