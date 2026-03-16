package org.cdpg.dx.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseUtilNGSILDTest {

  @Test
  @DisplayName("generateResponse(statusCode, urn) should use status description and empty instance")
  void generateResponseTwoArgs() {
    JsonObject result =
        ResponseUtilNGSILD.generateResponse(HttpStatusCode.NOT_FOUND, "urn:dx:rs:notFound");

    assertThat(result.getString("type")).isEqualTo("urn:dx:rs:notFound");
    assertThat(result.getString("title")).isEqualTo("Not Found");
    assertThat(result.getString("detail")).isEqualTo("Not Found");
    assertThat(result.getString("instance")).isEqualTo("");
  }

  @Test
  @DisplayName(
      "generateResponse(statusCode, urn, message, instance) should use custom message and instance")
  void generateResponseFourArgs() {
    JsonObject result =
        ResponseUtilNGSILD.generateResponse(
            HttpStatusCode.BAD_REQUEST, "urn:dx:rs:badRequest", "Invalid query", "host:8443");

    assertThat(result.getString("type")).isEqualTo("urn:dx:rs:badRequest");
    assertThat(result.getString("title")).isEqualTo("Bad Request");
    assertThat(result.getString("detail")).isEqualTo("Invalid query");
    assertThat(result.getString("instance")).isEqualTo("host:8443");
  }
}
