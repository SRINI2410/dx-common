package org.cdpg.dx.common.request;

import static org.assertj.core.api.Assertions.*;

import org.cdpg.dx.common.exception.DxBadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TemporalRequestHelper Tests")
class TemporalRequestHelperTest {

  @Nested
  @DisplayName("buildTemporalRequest")
  class BuildTemporalRequestTests {

    @Test
    @DisplayName("null timeField should return null")
    void nullTimeFieldReturnsNull() {
      assertThat(TemporalRequestHelper.buildTemporalRequest(null, "after", "2025-01-01T00:00:00", null)).isNull();
    }

    @Test
    @DisplayName("blank timeField should return null")
    void blankTimeFieldReturnsNull() {
      assertThat(TemporalRequestHelper.buildTemporalRequest("  ", "after", "2025-01-01T00:00:00", null)).isNull();
    }

    @Test
    @DisplayName("between with valid times should return TemporalRequest")
    void betweenWithValidTimes() {
      TemporalRequest result = TemporalRequestHelper.buildTemporalRequest(
          "created_at", "between", "2025-01-01T00:00:00", "2025-12-31T23:59:59");
      assertThat(result).isNotNull();
      assertThat(result.timeField()).isEqualTo("created_at");
      assertThat(result.timeRel()).isEqualTo("between");
      assertThat(result.time()).isEqualTo("2025-01-01T00:00:00");
      assertThat(result.endtime()).isEqualTo("2025-12-31T23:59:59");
    }

    @Test
    @DisplayName("during is alias for between")
    void duringAlias() {
      TemporalRequest result = TemporalRequestHelper.buildTemporalRequest(
          "created_at", "during", "2025-01-01T00:00:00", "2025-06-01T00:00:00");
      assertThat(result).isNotNull();
      assertThat(result.timeRel()).isEqualTo("during");
    }

    @Test
    @DisplayName("between without endtime should throw DxBadRequestException")
    void betweenWithoutEndtime() {
      assertThatThrownBy(() -> TemporalRequestHelper.buildTemporalRequest(
          "created_at", "between", "2025-01-01T00:00:00", null))
          .isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("between with time after endtime should throw DxBadRequestException")
    void betweenTimeAfterEndtime() {
      assertThatThrownBy(() -> TemporalRequestHelper.buildTemporalRequest(
          "created_at", "between", "2025-12-31T23:59:59", "2025-01-01T00:00:00"))
          .isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("after should return TemporalRequest with null endtime")
    void afterQuery() {
      TemporalRequest result = TemporalRequestHelper.buildTemporalRequest(
          "created_at", "after", "2025-06-01T00:00:00", null);
      assertThat(result).isNotNull();
      assertThat(result.timeRel()).isEqualTo("after");
      assertThat(result.endtime()).isNull();
    }

    @Test
    @DisplayName("before should return TemporalRequest with null endtime")
    void beforeQuery() {
      TemporalRequest result = TemporalRequestHelper.buildTemporalRequest(
          "created_at", "before", "2025-06-01T00:00:00", null);
      assertThat(result).isNotNull();
      assertThat(result.timeRel()).isEqualTo("before");
    }

    @Test
    @DisplayName("after without time should throw DxBadRequestException")
    void afterWithoutTime() {
      assertThatThrownBy(() -> TemporalRequestHelper.buildTemporalRequest(
          "created_at", "after", null, null))
          .isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("invalid datetime format should throw DxBadRequestException")
    void invalidDateTimeFormat() {
      assertThatThrownBy(() -> TemporalRequestHelper.buildTemporalRequest(
          "created_at", "between", "not-a-date", "also-not-a-date"))
          .isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("unknown timerel should return null")
    void unknownTimerel() {
      assertThat(TemporalRequestHelper.buildTemporalRequest(
          "created_at", "unknown_rel", "2025-01-01T00:00:00", null)).isNull();
    }
  }
}
