package org.cdpg.dx.auth.appid.cache;

/**
 * Static holder for AppId cache instances, shared across verticles within the same JVM.
 *
 * <p>Populated once by DataBrokerVerticle (before ApiServerVerticle starts) and read by
 * ApiServerVerticle to wire the caches into auth handlers.
 */
public class AppIdCacheHolder {

  private static AppIdCacheService appIdCacheService;
  private static AppIdItemAccessCacheService itemAccessCacheService;

  private AppIdCacheHolder() {}

  public static void register(AppIdCacheService appIdCache, AppIdItemAccessCacheService itemAccessCache) {
    appIdCacheService = appIdCache;
    itemAccessCacheService = itemAccessCache;
  }

  public static AppIdCacheService getAppIdCache() {
    return appIdCacheService;
  }

  public static AppIdItemAccessCacheService getItemAccessCache() {
    return itemAccessCacheService;
  }
}
