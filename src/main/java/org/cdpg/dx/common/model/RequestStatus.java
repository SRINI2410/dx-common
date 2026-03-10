package org.cdpg.dx.common.model;

import java.util.stream.Stream;
import org.cdpg.dx.common.exception.BaseDxException;

public enum RequestStatus {
  REJECTED("rejected"),
  PENDING("pending"),
  GRANTED("granted"),
  WITHDRAWN("withdrawn");
  private final String status;

  RequestStatus(String value) {
    status = value;
  }

  public static RequestStatus fromString(String requestStatus) {
    return Stream.of(values())
        .filter(element -> element.getRequestStatus().equalsIgnoreCase(requestStatus))
        .findAny()
        .orElseThrow(
            () -> new BaseDxException(404, "Document of given id does not exist"));
  }

  public String getRequestStatus() {
    return status;
  }
}
