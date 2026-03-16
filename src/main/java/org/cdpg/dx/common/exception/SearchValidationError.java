package org.cdpg.dx.common.exception;

/** Exception for search query validation errors. */
public class SearchValidationError extends DxSearchException {

  public SearchValidationError(String message) {
    super(DxErrorCodes.VALIDATION_ERROR, message);
  }

  public SearchValidationError(String message, Throwable cause) {
    super(DxErrorCodes.VALIDATION_ERROR, message, cause);
  }
}
