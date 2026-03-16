package org.cdpg.dx.common.exception;

/** Exception for invalid JSON path operations in Redis. */
public class InvalidJsonPathException extends DxRedisException {
  public InvalidJsonPathException(String message) {
    super(DxErrorCodes.INVALID_JSON_PATH, message);
  }
}
