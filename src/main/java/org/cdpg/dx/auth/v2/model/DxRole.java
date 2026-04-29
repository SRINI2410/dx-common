package org.cdpg.dx.auth.v2.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The five immutable system roles, pre-configured in Keycloak and authoritative in code.
 *
 * <p>Roles are identity labels; they map to scopes via {@link
 * org.cdpg.dx.auth.v2.registry.SystemRoleScopeMap}. Authorization is enforced on scopes, not roles.
 *
 * <p>Not to be confused with the legacy {@code org.cdpg.dx.auth.authorization.model.DxRole} (8
 * values, including delegate variants) which is retained unchanged during migration.
 */
public enum DxRole {
  CONSUMER("consumer"),
  PROVIDER("provider"),
  ORG_ADMIN("org_admin"),
  COS_ADMIN("cos_admin"),
  COMPUTE("compute");

  private static final Map<String, DxRole> LOOKUP =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(r -> r.keycloakName, r -> r));

  private final String keycloakName;

  DxRole(String keycloakName) {
    this.keycloakName = keycloakName;
  }

  public String keycloakName() {
    return keycloakName;
  }

  public static Optional<DxRole> fromKeycloakName(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(LOOKUP.get(name.toLowerCase()));
  }
}