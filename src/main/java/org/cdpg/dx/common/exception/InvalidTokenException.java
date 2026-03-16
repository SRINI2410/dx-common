package org.cdpg.dx.common.exception;

/** Exception for invalid authentication tokens. */
public class InvalidTokenException extends BaseDxException {
  public InvalidTokenException(String message) {
    super(DxErrorCodes.TOKEN_INVALID, message);
  }
}
