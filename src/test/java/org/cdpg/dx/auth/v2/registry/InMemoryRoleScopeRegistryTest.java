package org.cdpg.dx.auth.v2.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.cdpg.dx.auth.v2.model.DxPrincipal;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.auth.v2.model.Scopes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryRoleScopeRegistry Tests")
class InMemoryRoleScopeRegistryTest {

  private final RoleScopeRegistry registry = new InMemoryRoleScopeRegistry();

  @Nested
  @DisplayName("Plain user — flattens roles")
  class PlainUser {

    @Test
    @DisplayName("single role yields its scope bundle")
    void singleRole() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("alice")
              .authenticatedOrgId("org-a")
              .authorizationRoles(Set.of(DxRole.PROVIDER))
              .build();
      assertEquals(Set.of(Scopes.OWN_ASSET_MANAGEMENT), registry.resolveEffectiveScopes(p));
    }

    @Test
    @DisplayName("two roles yield the union of their scopes")
    void twoRolesUnion() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("alice")
              .authenticatedOrgId("org-a")
              .authorizationRoles(Set.of(DxRole.CONSUMER, DxRole.PROVIDER))
              .build();
      assertEquals(
          Set.of(Scopes.DATA_ACCESS, Scopes.OWN_ASSET_MANAGEMENT),
          registry.resolveEffectiveScopes(p));
    }

    @Test
    @DisplayName("no roles yields an empty set")
    void noRoles() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("alice")
              .authenticatedOrgId("org-a")
              .build();
      assertTrue(registry.resolveEffectiveScopes(p).isEmpty());
    }
  }

  @Nested
  @DisplayName("Delegation / App — uses directScopes")
  class DirectScopes {

    @Test
    @DisplayName("delegation principal (empty roles + direct scopes) returns direct scopes")
    void delegation() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("bob")
              .authenticatedOrgId("org-b")
              .delegatorSub("alice")
              .delegatorOrgId("org-a")
              .directScopes(Set.of(Scopes.DATA_ACCESS))
              .build();
      assertEquals(Set.of(Scopes.DATA_ACCESS), registry.resolveEffectiveScopes(p));
    }

    @Test
    @DisplayName("app principal returns direct scopes only")
    void app() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("ananjay")
              .authenticatedOrgId("org-a")
              .appId("analytics-worker")
              .directScopes(Set.of(Scopes.OWN_ASSET_MANAGEMENT))
              .build();
      assertEquals(Set.of(Scopes.OWN_ASSET_MANAGEMENT), registry.resolveEffectiveScopes(p));
    }
  }

  @Nested
  @DisplayName("Mixed roles + directScopes (future phase — JWT scope claim)")
  class Mixed {

    @Test
    @DisplayName("both sources union correctly")
    void bothUnion() {
      DxPrincipal p =
          DxPrincipal.builder()
              .authenticatedSub("alice")
              .authenticatedOrgId("org-a")
              .authorizationRoles(Set.of(DxRole.PROVIDER))
              .directScopes(Set.of(Scopes.ASSET_PUBLISH))
              .build();
      assertEquals(
          Set.of(Scopes.OWN_ASSET_MANAGEMENT, Scopes.ASSET_PUBLISH),
          registry.resolveEffectiveScopes(p));
    }
  }
}