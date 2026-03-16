package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.cdpg.dx.common.exception.DxValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DateTimeHelper Tests")
class DateTimeHelperTest {

  @Nested
  @DisplayName("getCurrentTimeString")
  class GetCurrentTimeStringTests {
    @Test
    @DisplayName("should return non-null ISO formatted string")
    void returnsNonNull() {
      String result = DateTimeHelper.getCurrentTimeString();
      assertThat(result).isNotNull().isNotBlank();
      assertThat(LocalDateTime.parse(result)).isNotNull();
    }
  }

  @Nested
  @DisplayName("parse")
  class ParseTests {
    @Test
    @DisplayName("valid ISO datetime should parse successfully")
    void validParse() {
      LocalDateTime result = DateTimeHelper.parse("2025-06-04T12:30:00");
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo(2025);
      assertThat(result.getMonthValue()).isEqualTo(6);
    }

    @Test
    @DisplayName("invalid datetime should return null")
    void invalidReturnsNull() {
      assertThat(DateTimeHelper.parse("not-a-date")).isNull();
    }

    @Test
    @DisplayName("null should return null")
    void nullReturnsNull() {
      assertThat(DateTimeHelper.parse(null)).isNull();
    }
  }

  @Nested
  @DisplayName("format")
  class FormatTests {
    @Test
    @DisplayName("should format LocalDateTime to ISO string")
    void formatsCorrectly() {
      LocalDateTime dt = LocalDateTime.of(2025, 6, 4, 12, 30, 0);
      assertThat(DateTimeHelper.format(dt)).isEqualTo("2025-06-04T12:30:00");
    }
  }

  @Nested
  @DisplayName("parseDateTime")
  class ParseDateTimeTests {
    @Test
    @DisplayName("null should return null")
    void nullReturnsNull() {
      assertThat(DateTimeHelper.parseDateTime(null)).isNull();
    }

    @Test
    @DisplayName("blank should return null")
    void blankReturnsNull() {
      assertThat(DateTimeHelper.parseDateTime("  ")).isNull();
    }

    @Test
    @DisplayName("valid string should parse")
    void validParses() {
      assertThat(DateTimeHelper.parseDateTime("2025-06-04T12:30:00")).isNotNull();
    }
  }

  @Nested
  @DisplayName("parseRequiredDateTime")
  class ParseRequiredDateTimeTests {
    @Test
    @DisplayName("valid value should parse")
    void validParses() {
      LocalDateTime result = DateTimeHelper.parseRequiredDateTime("2025-06-04T12:30:00", "startTime");
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("null should throw DxValidationException")
    void nullThrows() {
      assertThatThrownBy(() -> DateTimeHelper.parseRequiredDateTime(null, "startTime"))
          .isInstanceOf(DxValidationException.class)
          .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("invalid format should throw DxValidationException")
    void invalidThrows() {
      assertThatThrownBy(() -> DateTimeHelper.parseRequiredDateTime("bad-date", "startTime"))
          .isInstanceOf(DxValidationException.class);
    }
  }
}
