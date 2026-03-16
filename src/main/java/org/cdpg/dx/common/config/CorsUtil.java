package org.cdpg.dx.common.config;

import java.util.List;

/**
 * Shared CORS configuration used across all DX microservices.
 *
 * <p>The {@link #allowedOrigins} list should be set during application startup
 * from the service configuration (e.g. {@code corsAllowedOrigin} in Vert.x config).
 */
public class CorsUtil {

  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

  public static List<String> allowedOrigins;
}
