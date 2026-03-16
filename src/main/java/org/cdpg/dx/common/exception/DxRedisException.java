package org.cdpg.dx.common.exception;

/** Base exception for Redis-related errors. */
public class DxRedisException extends BaseDxException {

  public DxRedisException(String message) {
    super(DxErrorCodes.REDIS_ERROR, message);
  }

  public DxRedisException(String message, Throwable cause) {
    super(DxErrorCodes.REDIS_ERROR, message, cause);
  }

  public DxRedisException(int code, String message) {
    super(code, message);
  }

  public DxRedisException(int code, String message, Throwable cause) {
    super(code, message, cause);
  }
}
