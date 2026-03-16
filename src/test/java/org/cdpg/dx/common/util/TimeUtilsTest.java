package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.cdpg.dx.common.exception.DxValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DateTimeHelper Tests")
class TimeUtilsTest {

  @Nested
  @DisplayName("getCurrentTimeString()")
  class GetCurrentTimeStringTests {

    @Test
    @DisplayName("should return a non-null ISO date-time string")
    void returnsNonNullString() {
      String result = DateTimeHelper.getCurrentTimeString();
      assertThat(result).isNotNull().isNotEmpty();
      // Should be parseable back
      LocalDateTime parsed = LocalDateTime.parse(result, DateTimeHelper.FORMATTER);
      assertThat(parsed).isNotNull();
    }

    @Test
    @DisplayName("should return formatted string with custom pattern")
    void returnsFormattedWithCustomPattern() {
      String result = DateTimeHelper.getCurrentTimeString("yyyy-MM-dd");
      assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}");
    }
  }

  @Nested
  @DisplayName("parse()")
  class ParseTests {

    @Test
    @DisplayName("should parse valid ISO local date-time string")
    void parsesValidIsoDateTime() {
      LocalDateTime result = DateTimeHelper.parse("2025-10-01T12:30:00");
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo(2025);
      assertThat(result.getMonthValue()).isEqualTo(10);
      assertThat(result.getDayOfMonth()).isEqualTo(1);
      assertThat(result.getHour()).isEqualTo(12);
      assertThat(result.getMinute()).isEqualTo(30);
    }

    @Test
    @DisplayName("should return null for invalid date-time string")
    void returnsNullForInvalidString() {
      assertThat(DateTimeHelper.parse("not-a-date")).isNull();
    }

    @Test
    @DisplayName("should return null for null input")
    void returnsNullForNull() {
      assertThat(DateTimeHelper.parse(null)).isNull();
    }
  }

  @Nested
  @DisplayName("format()")
  class FormatTests {

    @Test
    @DisplayName("should format LocalDateTime to ISO string")
    void formatsToIsoString() {
      LocalDateTime dt = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
      String result = DateTimeHelper.format(dt);
      assertThat(result).isEqualTo("2025-06-15T10:30:00");
    }
  }

  @Nested
  @DisplayName("parseDateTime()")
  class ParseDateTimeTests {

    @Test
    @DisplayName("should parse valid string to LocalDateTime")
    void parsesValidString() {
      LocalDateTime result = DateTimeHelper.parseDateTime("2025-10-01T00:00:00");
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo(2025);
    }

    @Test
    @DisplayName("should return null for null input")
    void returnsNullForNull() {
      assertThat(DateTimeHelper.parseDateTime(null)).isNull();
    }

    @Test
    @DisplayName("should return null for blank input")
    void returnsNullForBlank() {
      assertThat(DateTimeHelper.parseDateTime("   ")).isNull();
    }

    @Test
    @DisplayName("should return null for empty input")
    void returnsNullForEmpty() {
      assertThat(DateTimeHelper.parseDateTime("")).isNull();
    }
  }

  @Nested
  @DisplayName("parseRequiredDateTime()")
  class ParseRequiredDateTimeTests {

    @Test
    @DisplayName("should parse valid required datetime")
    void parsesValidRequired() {
      LocalDateTime result = DateTimeHelper.parseRequiredDateTime("2025-10-01T00:00:00", "startDate");
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo(2025);
    }

    @Test
    @DisplayName("should throw DxValidationException for null value")
    void throwsForNull() {
      assertThatThrownBy(() -> DateTimeHelper.parseRequiredDateTime(null, "startDate"))
          .isInstanceOf(DxValidationException.class)
          .hasMessageContaining("required timestamp missing");
    }

    @Test
    @DisplayName("should throw DxValidationException for blank value")
    void throwsForBlank() {
      assertThatThrownBy(() -> DateTimeHelper.parseRequiredDateTime("   ", "startDate"))
          .isInstanceOf(DxValidationException.class)
          .hasMessageContaining("required timestamp missing");
    }

    @Test
    @DisplayName("should throw DxValidationException for invalid format")
    void throwsForInvalidFormat() {
      assertThatThrownBy(() -> DateTimeHelper.parseRequiredDateTime("not-a-date", "startDate"))
          .isInstanceOf(DxValidationException.class)
          .hasMessageContaining("Parsing of LocalDateTime unsuccessful");
    }
  }
}
