package org.cdpg.dx.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DxErrorResponseTest {

  @Test
  @DisplayName("toJson() should contain type, title, and detail fields")
  void toJsonContainsAllFields() {
    DxErrorResponse response =
        new DxErrorResponse("urn:dx:acl:badRequest", "Bad Request", "invalid input");

    JsonObject json = response.toJson();

    assertThat(json.getString("type")).isEqualTo("urn:dx:acl:badRequest");
    assertThat(json.getString("title")).isEqualTo("Bad Request");
    assertThat(json.getString("detail")).isEqualTo("invalid input");
  }

  @Test
  @DisplayName("toJson() should handle null values gracefully")
  void toJsonHandlesNulls() {
    DxErrorResponse response = new DxErrorResponse(null, null, null);

    JsonObject json = response.toJson();

    assertThat(json.getString("type")).isNull();
    assertThat(json.getString("title")).isNull();
    assertThat(json.getString("detail")).isNull();
  }
}
