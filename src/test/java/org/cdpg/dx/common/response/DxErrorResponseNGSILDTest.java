package org.cdpg.dx.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DxErrorResponseNGSILDTest {

  @Test
  @DisplayName("toJson() should contain type, title, detail, and instance fields")
  void toJsonContainsAllFields() {
    DxErrorResponseNGSILD response =
        new DxErrorResponseNGSILD(
            "urn:dx:rs:notFound", "Not Found", "entity missing", "localhost:8443");

    JsonObject json = response.toJson();

    assertThat(json.getString("type")).isEqualTo("urn:dx:rs:notFound");
    assertThat(json.getString("title")).isEqualTo("Not Found");
    assertThat(json.getString("detail")).isEqualTo("entity missing");
    assertThat(json.getString("instance")).isEqualTo("localhost:8443");
  }

  @Test
  @DisplayName("toJson() should handle null values gracefully")
  void toJsonHandlesNulls() {
    DxErrorResponseNGSILD response = new DxErrorResponseNGSILD(null, null, null, null);

    JsonObject json = response.toJson();

    assertThat(json.fieldNames()).containsExactlyInAnyOrder("type", "title", "detail", "instance");
  }
}
