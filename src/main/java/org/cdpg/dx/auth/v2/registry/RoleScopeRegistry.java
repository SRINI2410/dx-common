package org.cdpg.dx.auth.v2.registry;

import java.util.Set;
import org.cdpg.dx.auth.v2.model.DxPrincipal;

/**
 * Resolves a principal's effective scope set. One method, one behavior — correct for plain users,
 * delegation, and apps alike.
 */
public interface RoleScopeRegistry {

  Set<String> resolveEffectiveScopes(DxPrincipal principal);
}