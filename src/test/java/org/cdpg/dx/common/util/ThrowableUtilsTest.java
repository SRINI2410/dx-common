package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.cdpg.dx.common.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ThrowableUtilsTest {

  @Nested
  @DisplayName("isSafeToExpose()")
  class IsSafeToExposeTests {

    @Test
    @DisplayName("should return true for IllegalArgumentException")
    void illegalArgumentExceptionIsSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new IllegalArgumentException("bad arg")))
          .isTrue();
    }

    @Test
    @DisplayName("should return true for BaseDxException")
    void baseDxExceptionIsSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new BaseDxException("base error")))
          .isTrue();
    }

    @Test
    @DisplayName("should return true for BaseDxException subclasses")
    void baseDxExceptionSubclassesAreSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new DxNotFoundException("not found")))
          .isTrue();
      assertThat(ThrowableUtils.isSafeToExpose(new DxBadRequestException("bad request")))
          .isTrue();
      assertThat(ThrowableUtils.isSafeToExpose(new DxForbiddenException("forbidden")))
          .isTrue();
      assertThat(ThrowableUtils.isSafeToExpose(new NoRowFoundException("no row")))
          .isTrue();
      assertThat(ThrowableUtils.isSafeToExpose(new DxInternalServerErrorException("internal")))
          .isTrue();
    }

    @Test
    @DisplayName("should return false for RuntimeException")
    void runtimeExceptionIsNotSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new RuntimeException("runtime")))
          .isFalse();
    }

    @Test
    @DisplayName("should return false for NullPointerException")
    void nullPointerExceptionIsNotSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new NullPointerException("npe")))
          .isFalse();
    }

    @Test
    @DisplayName("should return false for checked exceptions")
    void checkedExceptionsAreNotSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new Exception("generic")))
          .isFalse();
    }

    @Test
    @DisplayName("should return false for Error")
    void errorsAreNotSafe() {
      assertThat(ThrowableUtils.isSafeToExpose(new OutOfMemoryError("oom")))
          .isFalse();
    }
  }
}
