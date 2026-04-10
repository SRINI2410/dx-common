package org.cdpg.dx.auth.appid.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.auth.appid.model.AppIdPrincipal;

/**
 * In-process cache for AppId principals, keyed by appId.
 *
 * <p>Cache key is appId only (not appId+secret). The TTL bounds the revocation propagation window —
 * a revoked credential will still be accepted for up to {@code ttlMinutes} minutes until the cache
 * entry expires or is explicitly invalidated.
 *
 * <p>Invalidation is triggered externally (e.g., via a revocation event stream) by calling {@link
 * #invalidate(String)}.
 */
public class AppIdCacheService {

  private final Cache<String, AppIdPrincipal> cache;

  public AppIdCacheService(int maxSize, long ttlMinutes) {
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .build();
  }

  public Optional<AppIdPrincipal> get(String appId) {
    return Optional.ofNullable(cache.getIfPresent(appId));
  }

  public void put(String appId, AppIdPrincipal principal) {
    cache.put(appId, principal);
  }

  public void invalidate(String appId) {
    cache.invalidate(appId);
  }
}
