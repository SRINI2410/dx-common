package org.cdpg.dx.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RestResponseNGSILDTest {

  @Nested
  @DisplayName("Builder.build()")
  class BuilderBuildTests {

    @Test
    @DisplayName("should build with type, title, detail, and instance")
    void buildsWithAllFields() {
      RestResponseNGSILD response =
          new RestResponseNGSILD.Builder()
              .withType("urn:dx:rs:success")
              .withTitle("Success")
              .withMessage("entities returned")
              .withInstance("localhost:8443")
              .build();

      JsonObject json = response.toJson();

      assertThat(json.getString("type")).isEqualTo("urn:dx:rs:success");
      assertThat(json.getString("title")).isEqualTo("Success");
      assertThat(json.getString("detail")).isEqualTo("entities returned");
      assertThat(json.getString("instance")).isEqualTo("localhost:8443");
      assertThat(json.containsKey("statusCode")).isFalse();
    }
  }

  @Nested
  @DisplayName("Builder.build(statusCode, ...)")
  class BuilderBuildWithStatusTests {

    @Test
    @DisplayName("should include statusCode in JSON when built with 5-arg build()")
    void includesStatusCode() {
      RestResponseNGSILD response =
          new RestResponseNGSILD.Builder()
              .build(200, "urn:dx:rs:success", "Success", "done", "host:8443");

      JsonObject json = response.toJson();

      assertThat(json.getInteger("statusCode")).isEqualTo(200);
      assertThat(json.getString("instance")).isEqualTo("host:8443");
    }
  }

  @Nested
  @DisplayName("toJson()")
  class ToJsonTests {

    @Test
    @DisplayName("should not include statusCode when status is 0")
    void excludesStatusCodeWhenZero() {
      RestResponseNGSILD response =
          new RestResponseNGSILD.Builder()
              .withType("test")
              .withTitle("title")
              .withMessage("msg")
              .withInstance("host")
              .build();

      JsonObject json = response.toJson();

      assertThat(json.containsKey("statusCode")).isFalse();
      assertThat(json.fieldNames())
          .containsExactlyInAnyOrder("type", "title", "detail", "instance");
    }
  }
}
