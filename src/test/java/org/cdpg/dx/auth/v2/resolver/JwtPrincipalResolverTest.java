package org.cdpg.dx.auth.v2.resolver;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.model.DxPrincipal;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtPrincipalResolver Tests")
class JwtPrincipalResolverTest {

  private final JwtPrincipalResolver resolver = new JwtPrincipalResolver();

  private static JsonObject jwt(String sub, String org, String... roles) {
    JsonArray rolesArr = new JsonArray();
    for (String r : roles) rolesArr.add(r);
    return new JsonObject()
        .put("sub", sub)
        .put("organisation_id", org)
        .put("realm_access", new JsonObject().put("roles", rolesArr));
  }

  @Test
  @DisplayName("builds direct-user principal from JWT claims")
  void happyPath() {
    FakeRoutingContext fake =
        new FakeRoutingContext().userWithClaims(jwt("alice", "org-a", "org_admin"));

    resolver.resolve(fake.ctx);

    assertTrue(fake.nextCalled);
    DxPrincipal p = (DxPrincipal) fake.data.get(AuthorizationHandler.PRINCIPAL_KEY);
    assertNotNull(p);
    assertEquals("alice", p.getSub());
    assertEquals("org-a", p.getOrganisationId());
    assertEquals(Set.of(DxRole.ORG_ADMIN), p.getAuthorizationRoles());
    assertEquals(Set.of(DxRole.ORG_ADMIN), p.getAuditRoles());
    assertTrue(p.isDirectUser());
  }

  @Test
  @DisplayName("skips unknown Keycloak role names silently")
  void unknownRolesSkipped() {
    FakeRoutingContext fake =
        new FakeRoutingContext()
            .userWithClaims(jwt("alice", "org-a", "org_admin", "totally_made_up"));

    resolver.resolve(fake.ctx);

    DxPrincipal p = (DxPrincipal) fake.data.get(AuthorizationHandler.PRINCIPAL_KEY);
    assertEquals(Set.of(DxRole.ORG_ADMIN), p.getAuthorizationRoles());
  }

  @Test
  @DisplayName("handles JWT with no roles (empty set, still builds principal)")
  void noRolesOk() {
    FakeRoutingContext fake = new FakeRoutingContext().userWithClaims(jwt("alice", "org-a"));

    resolver.resolve(fake.ctx);

    DxPrincipal p = (DxPrincipal) fake.data.get(AuthorizationHandler.PRINCIPAL_KEY);
    assertTrue(p.getAuthorizationRoles().isEmpty());
  }

  @Test
  @DisplayName("missing user → 401")
  void noUser() {
    FakeRoutingContext fake = new FakeRoutingContext().noUser();
    resolver.resolve(fake.ctx);
    assertInstanceOf(DxUnauthorizedException.class, fake.failedWith);
  }

  @Test
  @DisplayName("missing sub → 401")
  void missingSub() {
    FakeRoutingContext fake =
        new FakeRoutingContext()
            .userWithClaims(new JsonObject().put("organisation_id", "org-a"));
    resolver.resolve(fake.ctx);
    assertInstanceOf(DxUnauthorizedException.class, fake.failedWith);
  }

  @Test
  @DisplayName("missing organisation_id → 401")
  void missingOrg() {
    FakeRoutingContext fake =
        new FakeRoutingContext().userWithClaims(new JsonObject().put("sub", "alice"));
    resolver.resolve(fake.ctx);
    assertInstanceOf(DxUnauthorizedException.class, fake.failedWith);
  }
}