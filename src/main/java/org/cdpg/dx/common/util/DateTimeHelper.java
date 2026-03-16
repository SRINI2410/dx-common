package org.cdpg.dx.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.cdpg.dx.common.exception.DxValidationException;

/**
 * Date/time utility methods shared across DX microservices.
 *
 * <p>Uses ISO-8601 local date-time format (e.g., {@code 2025-06-04T12:30:00}).
 */
public class DateTimeHelper {

  public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private DateTimeHelper() {}

  /** Returns current time formatted as ISO local date-time string. */
  public static String getCurrentTimeString() {
    return LocalDateTime.now().format(FORMATTER);
  }

  /** Returns current time formatted with a custom pattern. */
  public static String getCurrentTimeString(String pattern) {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
  }

  /** Parses an ISO local date-time string, returns null on failure. */
  public static LocalDateTime parse(String dateTimeStr) {
    try {
      return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception e) {
      return null;
    }
  }

  /** Formats a LocalDateTime to ISO local date-time string. */
  public static String format(LocalDateTime dateTime) {
    return dateTime.format(FORMATTER);
  }

  /** Parses a nullable/blank string to LocalDateTime, returns null if empty. */
  public static LocalDateTime parseDateTime(String value) {
    if (value == null || value.isBlank()) return null;
    return LocalDateTime.parse(value, FORMATTER);
  }

  /**
   * Parses a required datetime string, throwing {@link DxValidationException} on failure.
   *
   * @param value the datetime string to parse
   * @param name the field name (for error messages)
   * @return the parsed LocalDateTime
   * @throws DxValidationException if value is null/blank or unparseable
   */
  public static LocalDateTime parseRequiredDateTime(String value, String name) {
    if (value == null || value.isBlank())
      throw new DxValidationException("required timestamp missing for " + name);
    try {
      return LocalDateTime.parse(value, FORMATTER);
    } catch (Exception e) {
      throw new DxValidationException("Parsing of LocalDateTime unsuccessful!");
    }
  }
}
