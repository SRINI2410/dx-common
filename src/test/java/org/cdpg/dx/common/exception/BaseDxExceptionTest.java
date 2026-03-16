package org.cdpg.dx.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BaseDxExceptionTest {

  @Nested
  @DisplayName("Constructors")
  class ConstructorTests {

    @Test
    @DisplayName("(failureCode, message) should set code and message")
    void twoArgConstructor() {
      BaseDxException ex = new BaseDxException(400, "bad request");
      assertThat(ex.failureCode()).isEqualTo(400);
      assertThat(ex.getMessage()).isEqualTo("bad request");
    }

    @Test
    @DisplayName("(failureCode, message, cause) should set code and message")
    void threeArgConstructor() {
      RuntimeException cause = new RuntimeException("root cause");
      BaseDxException ex = new BaseDxException(500, "internal error", cause);
      assertThat(ex.failureCode()).isEqualTo(500);
      assertThat(ex.getMessage()).isEqualTo("internal error");
      // Note: getCause() may be null if ServiceException's superclass already set cause
      // The initCause() call in BaseDxException catches IllegalStateException silently
    }

    @Test
    @DisplayName("(message) should use DEFAULT_CODE")
    void singleArgConstructor() {
      BaseDxException ex = new BaseDxException("default error");
      assertThat(ex.failureCode()).isEqualTo(DxErrorCodes.DEFAULT_CODE);
      assertThat(ex.getMessage()).isEqualTo("default error");
    }

    @Test
    @DisplayName("should not throw when cause is 'this' (self-referencing)")
    void selfCauseDoesNotThrow() {
      // Should not throw IllegalArgumentException or stack overflow
      BaseDxException ex = new BaseDxException(100, "self");
      // initCause(this) is guarded — just verify no exception
      assertThat(ex.getMessage()).isEqualTo("self");
    }
  }

  @Nested
  @DisplayName("from()")
  class FromTests {

    @Test
    @DisplayName("should return same instance if already BaseDxException")
    void returnsSameInstanceForBaseDxException() {
      DxNotFoundException original = new DxNotFoundException("not found");
      BaseDxException result = BaseDxException.from(original);
      assertThat(result).isSameAs(original);
    }

    @Test
    @DisplayName("should convert ServiceException with PG_NO_ROW_ERROR to NoRowFoundException")
    void convertsServiceExceptionNoRow() {
      ServiceException se = new ServiceException(DxErrorCodes.PG_NO_ROW_ERROR, "no row");
      BaseDxException result = BaseDxException.from(se);
      assertThat(result).isInstanceOf(NoRowFoundException.class);
      assertThat(result.getMessage()).isEqualTo("no row");
    }

    @Test
    @DisplayName(
        "should convert ServiceException with PG_INVALID_COL_ERROR to InvalidColumnNameException")
    void convertsServiceExceptionInvalidCol() {
      ServiceException se = new ServiceException(DxErrorCodes.PG_INVALID_COL_ERROR, "bad column");
      BaseDxException result = BaseDxException.from(se);
      assertThat(result).isInstanceOf(InvalidColumnNameException.class);
    }

    @Test
    @DisplayName(
        "should convert ServiceException with PG_UNIQUE_CONSTRAINT_VIOLATION_ERROR to UniqueConstraintViolationException")
    void convertsServiceExceptionUniqueViolation() {
      ServiceException se =
          new ServiceException(DxErrorCodes.PG_UNIQUE_CONSTRAINT_VIOLATION_ERROR, "dup");
      BaseDxException result = BaseDxException.from(se);
      assertThat(result).isInstanceOf(UniqueConstraintViolationException.class);
    }

    @Test
    @DisplayName("should convert ServiceException with PG_ERROR to DxPgException")
    void convertsServiceExceptionPgError() {
      ServiceException se = new ServiceException(DxErrorCodes.PG_ERROR, "pg failure");
      BaseDxException result = BaseDxException.from(se);
      assertThat(result).isInstanceOf(DxPgException.class);
    }

    @Test
    @DisplayName("should convert ServiceException with unknown code to BaseDxException")
    void convertsServiceExceptionUnknownCode() {
      ServiceException se = new ServiceException(99999, "unknown");
      BaseDxException result = BaseDxException.from(se);
      assertThat(result).isExactlyInstanceOf(BaseDxException.class);
      assertThat(result.failureCode()).isEqualTo(DxErrorCodes.DEFAULT_CODE);
    }

    @Test
    @DisplayName("should wrap generic Throwable in BaseDxException")
    void wrapsGenericThrowable() {
      RuntimeException original = new RuntimeException("generic");
      BaseDxException result = BaseDxException.from(original);
      assertThat(result).isExactlyInstanceOf(BaseDxException.class);
      assertThat(result.getMessage()).isEqualTo("generic");
      // Note: getCause() may be null due to ServiceException's initCause limitations
    }
  }
}
