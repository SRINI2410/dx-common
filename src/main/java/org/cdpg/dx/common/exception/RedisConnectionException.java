package org.cdpg.dx.common.exception;

/** Exception for Redis connection failures. */
public class RedisConnectionException extends DxRedisException {
  public RedisConnectionException(String message) {
    super(DxErrorCodes.CONNECTION_ERROR, message);
  }
}
