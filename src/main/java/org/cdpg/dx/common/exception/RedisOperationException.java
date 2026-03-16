package org.cdpg.dx.common.exception;

/** Exception for failed Redis operations. */
public class RedisOperationException extends DxRedisException {
  public RedisOperationException(String message) {
    super(DxErrorCodes.OPERATION_FAILED, message);
  }
}
