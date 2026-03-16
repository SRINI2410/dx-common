package org.cdpg.dx.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseUtilTest {

  @Test
  @DisplayName("generateResponse(statusCode, urn) should use status description as message")
  void generateResponseTwoArgs() {
    JsonObject result =
        ResponseUtil.generateResponse(HttpStatusCode.BAD_REQUEST, "urn:dx:acl:badRequest");

    assertThat(result.getString("type")).isEqualTo("urn:dx:acl:badRequest");
    assertThat(result.getString("title")).isEqualTo("Bad Request");
    assertThat(result.getString("detail")).isEqualTo("Bad Request");
  }

  @Test
  @DisplayName("generateResponse(statusCode, urn, message) should use custom message")
  void generateResponseThreeArgs() {
    JsonObject result =
        ResponseUtil.generateResponse(
            HttpStatusCode.BAD_REQUEST, "urn:dx:acl:badRequest", "Missing required field");

    assertThat(result.getString("type")).isEqualTo("urn:dx:acl:badRequest");
    assertThat(result.getString("title")).isEqualTo("Bad Request");
    assertThat(result.getString("detail")).isEqualTo("Missing required field");
  }

  @Test
  @DisplayName("should work with different status codes")
  void worksWithDifferentCodes() {
    JsonObject result =
        ResponseUtil.generateResponse(HttpStatusCode.NOT_FOUND, "urn:dx:acl:notFound");

    assertThat(result.getString("title")).isEqualTo("Not Found");
  }
}
