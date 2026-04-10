package org.cdpg.dx.auth.appid.model;

import java.util.List;
import java.util.Map;
import org.cdpg.dx.auth.appid.v1.AppIdPrincipalProto;

/**
 * Immutable principal derived from a successful gRPC VerifyAppId response.
 *
 * <p>{@code entityMetadataMap} holds the OQ2-resolved map of entityId → item metadata JSON
 * (policies, iid, resourceServer, accessPolicy). One entry per data_access constraint that
 * has a specific entity UUID. Empty map = wildcard-only or non-data-access scope.
 */
public record AppIdPrincipal(
    String appId,
    String ownerId,
    List<String> roles,
    List<String> scopes,
    Map<String, String> entityMetadataMap,
    long expiresAtEpoch) {

  public static AppIdPrincipal fromProto(AppIdPrincipalProto proto) {
    return new AppIdPrincipal(
        proto.getAppId(),
        proto.getOwnerId(),
        proto.getRolesList(),
        proto.getScopesList(),
        proto.getEntityMetadataMapMap(),
        proto.getExpiresAtEpoch());
  }

  /** Returns true if item metadata is available for the given entity ID. */
  public boolean hasMetadataFor(String entityId) {
    return entityMetadataMap != null && entityMetadataMap.containsKey(entityId);
  }

  /** Returns item metadata JSON for the given entity ID, or null if not present. */
  public String getMetadataFor(String entityId) {
    return entityMetadataMap == null ? null : entityMetadataMap.get(entityId);
  }
}
