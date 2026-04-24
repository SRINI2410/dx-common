package org.cdpg.dx.auth.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DxRole Tests")
class DxRoleTest {

  @Nested
  @DisplayName("fromKeycloakName")
  class FromKeycloakName {

    @Test
    @DisplayName("resolves each of the five system roles")
    void resolvesAllFiveRoles() {
      assertEquals(Optional.of(DxRole.CONSUMER), DxRole.fromKeycloakName("consumer"));
      assertEquals(Optional.of(DxRole.PROVIDER), DxRole.fromKeycloakName("provider"));
      assertEquals(Optional.of(DxRole.ORG_ADMIN), DxRole.fromKeycloakName("org_admin"));
      assertEquals(Optional.of(DxRole.COS_ADMIN), DxRole.fromKeycloakName("cos_admin"));
      assertEquals(Optional.of(DxRole.COMPUTE), DxRole.fromKeycloakName("compute"));
    }

    @Test
    @DisplayName("is case-insensitive")
    void caseInsensitive() {
      assertEquals(Optional.of(DxRole.ORG_ADMIN), DxRole.fromKeycloakName("ORG_ADMIN"));
      assertEquals(Optional.of(DxRole.COS_ADMIN), DxRole.fromKeycloakName("Cos_Admin"));
    }

    @Test
    @DisplayName("returns empty for unknown names")
    void unknownReturnsEmpty() {
      assertEquals(Optional.empty(), DxRole.fromKeycloakName("delegate"));
      assertEquals(Optional.empty(), DxRole.fromKeycloakName("admin"));
      assertEquals(Optional.empty(), DxRole.fromKeycloakName(""));
    }

    @Test
    @DisplayName("returns empty for null")
    void nullReturnsEmpty() {
      assertEquals(Optional.empty(), DxRole.fromKeycloakName(null));
    }
  }

  @Nested
  @DisplayName("keycloakName")
  class KeycloakName {

    @Test
    @DisplayName("round-trips through fromKeycloakName")
    void roundTrip() {
      for (DxRole role : DxRole.values()) {
        assertEquals(
            Optional.of(role),
            DxRole.fromKeycloakName(role.keycloakName()),
            "round-trip failed for " + role);
      }
    }
  }

  @Test
  @DisplayName("has exactly five values — design contract")
  void fiveRolesExactly() {
    assertEquals(5, DxRole.values().length);
  }
}