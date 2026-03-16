package org.cdpg.dx.keycloak.client;

import io.vertx.core.json.JsonObject;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Thread-safe singleton provider for the Keycloak admin client.
 *
 * <p>Reads configuration keys:
 * <ul>
 *   <li>{@code keycloakUrl} - Keycloak server URL</li>
 *   <li>{@code keycloakRealm} - Target realm</li>
 *   <li>{@code keycloakAdminClientId} - Admin client ID</li>
 *   <li>{@code keycloakAdminClientSecret} - Admin client secret</li>
 * </ul>
 */
public class KeycloakClientProvider {

  private static volatile Keycloak keycloakInstance;

  private KeycloakClientProvider() {}

  public static Keycloak getInstance(JsonObject config) {
    if (keycloakInstance == null) {
      synchronized (KeycloakClientProvider.class) {
        if (keycloakInstance == null) {
          keycloakInstance =
              KeycloakBuilder.builder()
                  .serverUrl(config.getString("keycloakUrl"))
                  .realm(config.getString("keycloakRealm"))
                  .clientId(config.getString("keycloakAdminClientId"))
                  .clientSecret(config.getString("keycloakAdminClientSecret"))
                  .grantType("client_credentials")
                  .build();
        }
      }
    }
    return keycloakInstance;
  }

  /** For testing only - allows resetting the singleton. */
  public static void reset() {
    synchronized (KeycloakClientProvider.class) {
      if (keycloakInstance != null) {
        keycloakInstance.close();
        keycloakInstance = null;
      }
    }
  }
}
