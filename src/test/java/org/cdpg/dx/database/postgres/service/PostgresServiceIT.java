package org.cdpg.dx.database.postgres.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.testutil.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration tests for {@link PostgresService} against a real PostgreSQL container via
 * Testcontainers.
 *
 * <p>These tests exercise the full service proxy serialization chain:
 * <pre>
 *   Test → EBProxy.toJson() → EventBus → ProxyHandler.fromJson() → PostgresServiceImpl → PG
 * </pre>
 *
 * <p>This validates that {@code InsertQuery}, {@code SelectQuery}, {@code UpdateQuery},
 * {@code DeleteQuery}, and {@code Condition} all correctly survive the JSON round-trip
 * through the Vert.x service proxy, including type restoration for UUID, LocalDateTime,
 * and OffsetDateTime values via {@code PostgresServiceImpl.addToTuple()}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresServiceIT extends PostgresTestBase {

  private static final String TABLE = "test_items";

  @Override
  protected String getTruncateSQL() {
    return "TRUNCATE TABLE test_items CASCADE";
  }

  // ------------------------------------------------------------------
  // Basic CRUD (String/VARCHAR values — simplest proxy roundtrip)
  // ------------------------------------------------------------------

  @Test
  @DisplayName("insert - should insert a row and return it with a generated UUID id")
  void testInsert(VertxTestContext ctx) {
    InsertQuery query =
        new InsertQuery(TABLE, List.of("name", "value"), List.of("Test Item", "42"));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          assertThat(result).isNotNull();
          assertThat(result.isRowsAffected()).isTrue();
          assertThat(result.getRows()).isNotNull();
          assertThat(result.getRows().size()).isEqualTo(1);
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.getString("id")).isNotNull();
          assertThat(row.getString("name")).isEqualTo("Test Item");
        });
  }

  @Test
  @DisplayName("select - should find previously inserted rows")
  void testSelect(VertxTestContext ctx) {
    String uniqueName = "Select-" + UUID.randomUUID().toString().substring(0, 8);
    InsertQuery insertQuery =
        new InsertQuery(TABLE, List.of("name", "value"), List.of(uniqueName, "10"));

    Condition condition = new Condition("name", Condition.Operator.EQUALS, List.of(uniqueName));
    SelectQuery selectQuery =
        new SelectQuery(TABLE, List.of("*"), condition, null, null, null, null);

    postgresService
        .insert(insertQuery)
        .compose(v -> postgresService.select(selectQuery, false))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);
                          JsonObject row = result.getRows().getJsonObject(0);
                          assertThat(row.getString("name")).isEqualTo(uniqueName);
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("update - should update fields and return the modified row")
  void testUpdate(VertxTestContext ctx) {
    InsertQuery insertQuery =
        new InsertQuery(TABLE, List.of("name", "value"), List.of("Update Item", "1"));

    postgresService
        .insert(insertQuery)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              Condition condition = new Condition("id", Condition.Operator.EQUALS, List.of(id));
              UpdateQuery updateQuery =
                  new UpdateQuery(TABLE, List.of("value"), List.of("99"), condition, null, null);
              return postgresService.update(updateQuery);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.isRowsAffected()).isTrue();
                          assertThat(result.getRows()).hasSize(1);
                          JsonObject row = result.getRows().getJsonObject(0);
                          assertThat(row.getString("value")).isEqualTo("99");
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("delete - should remove a row and report rowsAffected")
  void testDelete(VertxTestContext ctx) {
    InsertQuery insertQuery =
        new InsertQuery(TABLE, List.of("name", "value"), List.of("Delete Item", "5"));

    postgresService
        .insert(insertQuery)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              Condition condition = new Condition("id", Condition.Operator.EQUALS, List.of(id));
              DeleteQuery deleteQuery = new DeleteQuery(TABLE, condition, null, null);
              return postgresService.delete(deleteQuery);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.isRowsAffected()).isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("executeQuery - should execute raw SQL and return results")
  void testExecuteQuery(VertxTestContext ctx) {
    String insertSql = "INSERT INTO " + TABLE + " (name, value) VALUES ($1, $2) RETURNING *";
    JsonArray params = new JsonArray().add("Raw SQL Item").add("77");

    postgresService
        .executeQuery(insertSql, params)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              String selectSql = "SELECT * FROM " + TABLE + " WHERE id = $1::uuid";
              return postgresService.executeQuery(selectSql, new JsonArray().add(id));
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);
                          assertThat(result.getRows().getJsonObject(0).getString("name"))
                              .isEqualTo("Raw SQL Item");
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("QueryResult - should have correct structure after insert")
  void testQueryResultStructure(VertxTestContext ctx) {
    InsertQuery query =
        new InsertQuery(TABLE, List.of("name", "value"), List.of("Structure Item", "1"));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          assertThat(result).isInstanceOf(QueryResult.class);
          assertThat(result.getRows()).isNotNull().isInstanceOf(JsonArray.class);
          assertThat(result.isRowsAffected()).isTrue();
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.containsKey("id")).isTrue();
          assertThat(row.containsKey("name")).isTrue();
          assertThat(row.containsKey("value")).isTrue();
        });
  }

  @Test
  @DisplayName("ping - should return true when database is reachable")
  void testPing(VertxTestContext ctx) {
    assertFutureSuccess(postgresService.ping(), ctx, result -> assertThat(result).isTrue());
  }

  // ------------------------------------------------------------------
  // Service Proxy Serialization: Type-specific roundtrip tests
  // These test that addToTuple() correctly restores types lost during
  // JSON serialization through the event bus proxy.
  // ------------------------------------------------------------------

  @Test
  @DisplayName("proxy serialization - LocalDateTime should survive roundtrip for TIMESTAMP column")
  void testInsertWithTimestamp(VertxTestContext ctx) {
    String timestamp = "2025-06-04T12:30:00";

    InsertQuery query =
        new InsertQuery(
            TABLE,
            List.of("name", "created_at"),
            List.of("Timestamp Item", timestamp));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          assertThat(result.isRowsAffected()).isTrue();
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.getString("name")).isEqualTo("Timestamp Item");
          // PG returns timestamp; convertToQueryResult calls .toString() on LocalDateTime
          String createdAt = row.getString("created_at");
          assertThat(createdAt).startsWith("2025-06-04T12:30");
        });
  }

  @Test
  @DisplayName("proxy serialization - OffsetDateTime should survive roundtrip for TIMESTAMPTZ column")
  void testInsertWithTimestampTZ(VertxTestContext ctx) {
    String timestampTz = "2025-06-04T12:30:00+05:30";

    InsertQuery query =
        new InsertQuery(
            TABLE,
            List.of("name", "updated_at"),
            List.of("TimestampTZ Item", timestampTz));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          assertThat(result.isRowsAffected()).isTrue();
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.getString("name")).isEqualTo("TimestampTZ Item");
          // PG stores as UTC and returns OffsetDateTime; toString() converts it
          String updatedAt = row.getString("updated_at");
          assertThat(updatedAt).isNotNull();
        });
  }

  @Test
  @DisplayName("proxy serialization - Boolean should survive roundtrip for BOOLEAN column")
  void testInsertWithBoolean(VertxTestContext ctx) {
    // Boolean values survive JSON roundtrip natively (no addToTuple conversion needed)
    String insertSql =
        "INSERT INTO " + TABLE + " (name, is_active) VALUES ($1, $2) RETURNING *";
    JsonArray params = new JsonArray().add("Boolean Item").add(false);

    assertFutureSuccess(
        postgresService.executeQuery(insertSql, params),
        ctx,
        result -> {
          assertThat(result.isRowsAffected()).isTrue();
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.getBoolean("is_active")).isFalse();
        });
  }

  @Test
  @DisplayName("proxy serialization - JsonObject should survive roundtrip for JSONB column")
  void testInsertWithJsonb(VertxTestContext ctx) {
    JsonObject metadata = new JsonObject().put("key", "value").put("count", 42);
    String insertSql =
        "INSERT INTO " + TABLE + " (name, metadata) VALUES ($1, $2::jsonb) RETURNING *";
    JsonArray params = new JsonArray().add("JSONB Item").add(metadata);

    assertFutureSuccess(
        postgresService.executeQuery(insertSql, params),
        ctx,
        result -> {
          assertThat(result.isRowsAffected()).isTrue();
          JsonObject row = result.getRows().getJsonObject(0);
          JsonObject returnedMeta = row.getJsonObject("metadata");
          assertThat(returnedMeta).isNotNull();
          assertThat(returnedMeta.getString("key")).isEqualTo("value");
          assertThat(returnedMeta.getInteger("count")).isEqualTo(42);
        });
  }

  @Test
  @DisplayName("proxy serialization - UUID in Condition should survive roundtrip for WHERE clause")
  void testSelectWithUuidCondition(VertxTestContext ctx) {
    InsertQuery insertQuery =
        new InsertQuery(TABLE, List.of("name", "value"), List.of("UUID Condition Item", "abc"));

    postgresService
        .insert(insertQuery)
        .compose(
            insertResult -> {
              // The id is a UUID string returned from PG
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              // This UUID string goes through proxy → addToTuple restores it to UUID
              Condition condition = new Condition("id", Condition.Operator.EQUALS, List.of(id));
              SelectQuery selectQuery =
                  new SelectQuery(TABLE, List.of("*"), condition, null, null, null, null);
              return postgresService.select(selectQuery, false);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);
                          assertThat(result.getRows().getJsonObject(0).getString("name"))
                              .isEqualTo("UUID Condition Item");
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("proxy serialization - LocalDateTime in raw SQL should survive roundtrip")
  void testSelectWithTimestampCondition(VertxTestContext ctx) {
    String timestamp = "2025-01-01T00:00:00";

    InsertQuery insertQuery =
        new InsertQuery(
            TABLE,
            List.of("name", "created_at"),
            List.of("Future Item", "2025-06-01T00:00:00"));

    postgresService
        .insert(insertQuery)
        .compose(
            v -> {
              // Query with timestamp condition via raw SQL
              String selectSql =
                  "SELECT * FROM " + TABLE + " WHERE created_at > $1 AND name = $2";
              JsonArray params = new JsonArray().add(timestamp).add("Future Item");
              return postgresService.executeQuery(selectSql, params);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);
                          assertThat(result.getRows().getJsonObject(0).getString("name"))
                              .isEqualTo("Future Item");
                          ctx.completeNow();
                        })));
  }
}
