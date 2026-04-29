package org.cdpg.dx.auth.appid.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.auth.appid.model.AppIdItemAccessResult;

/**
 * In-process cache for AppId item-access results, keyed by "appId:entityId".
 *
 * <p>Invalidation by appId removes all entries for that appId (e.g., on revocation).
 */
public class AppIdItemAccessCacheService {

  private final Cache<String, AppIdItemAccessResult> cache;

  public AppIdItemAccessCacheService(int maxSize, long ttlMinutes) {
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .build();
  }

  public Optional<AppIdItemAccessResult> get(String appId, String entityId) {
    return Optional.ofNullable(cache.getIfPresent(key(appId, entityId)));
  }

  public void put(String appId, String entityId, AppIdItemAccessResult result) {
    cache.put(key(appId, entityId), result);
  }

  public void invalidate(String appId) {
    String prefix = appId + ":";
    cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
  }

  private String key(String appId, String entityId) {
    return appId + ":" + entityId;
  }
}
