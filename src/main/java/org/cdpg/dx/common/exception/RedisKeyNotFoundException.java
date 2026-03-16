package org.cdpg.dx.common.exception;

/** Exception for Redis key not found. */
public class RedisKeyNotFoundException extends DxRedisException {
  public RedisKeyNotFoundException(String message) {
    super(DxErrorCodes.KEY_NOT_FOUND, message);
  }
}
