package org.cdpg.dx.common.util;

import java.time.ZonedDateTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility for parsing ISO-8601 datetime strings with timezone handling.
 *
 * <p>Accepts forms like:
 * <ul>
 *   <li>{@code 2025-10-01T00:00:00Z}</li>
 *   <li>{@code 2025-10-01T00:00:00+05:30}</li>
 *   <li>{@code 2025-10-01T00:00:00 05:30} (auto-fixes space to +)</li>
 * </ul>
 */
public class TimeUtils {
  private static final Logger LOGGER = LogManager.getLogger(TimeUtils.class);

  private TimeUtils() {}

  /**
   * Parses an ISO-8601 datetime string safely, normalizing whitespace to '+'.
   * Does NOT normalize or convert zones — returns as given.
   *
   * @param value the datetime string to parse
   * @return the normalized datetime string
   * @throws IllegalArgumentException if value is null, blank, or invalid format
   */
  public static String parseIsoDateTime(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Datetime value cannot be null or blank");
    }

    String normalized = value.trim().replaceAll("\\s", "+");
    try {
      ZonedDateTime zdt = ZonedDateTime.parse(normalized);
      LOGGER.debug("Parsed time: {}", zdt);
      return normalized;
    } catch (Exception e) {
      LOGGER.error("Failed to parse datetime: {}", e.getMessage());
      throw new IllegalArgumentException("Invalid datetime format: " + value);
    }
  }
}
