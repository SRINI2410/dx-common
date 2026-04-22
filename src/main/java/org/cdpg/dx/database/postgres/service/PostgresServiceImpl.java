package org.cdpg.dx.database.postgres.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.util.DxPgExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PostgresService} that executes queries against a Vert.x reactive PG
 * connection pool.
 *
 * <h3>Service Proxy Serialization</h3>
 *
 * When this service is accessed via the Vert.x event bus proxy, all method parameters are
 * serialized to JSON and deserialized on the receiving side. This causes <b>type erasure</b> for
 * non-JSON-native types:
 *
 * <ul>
 *   <li>{@code LocalDateTime} → becomes {@code String} (e.g., "2025-06-04T12:30:00")
 *   <li>{@code OffsetDateTime} → becomes {@code String} (e.g., "2025-06-04T12:30:00+05:30")
 * </ul>
 *
 * The {@link #addToTuple(Tuple, Object)} method detects these string-encoded temporal types and
 * restores them before adding to the SQL {@link Tuple}. UUID strings are left as plain strings
 * because PostgreSQL's implicit text→UUID cast handles UUID-typed columns correctly, and
 * VARCHAR columns that store UUID-formatted values must not receive a java.util.UUID object.
 */
public class PostgresServiceImpl implements PostgresService {
  private static final Logger LOG = LoggerFactory.getLogger(PostgresServiceImpl.class);

  private final Pool client;

  public PostgresServiceImpl(Pool client) {
    this.client = client;
  }

  private QueryResult convertToQueryResult(RowSet<Row> rowSet) {
    LOG.trace("convertToQueryResult() invoked");
    JsonArray jsonArray = new JsonArray();
    Object value;
    for (Row row : rowSet) {
      JsonObject json = new JsonObject();
      for (int i = 0; i < row.size(); i++) {
        String column = row.getColumnName(i);
        value = row.getValue(i);
        if (value == null
            || value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof JsonObject
            || value instanceof JsonArray) {
          json.put(column, value);
        } else {
          json.put(column, value.toString());
        }
      }
      jsonArray.add(json);
    }

    boolean rowsAffected = rowSet.rowCount() > 0;
    if (rowsAffected) {
      LOG.info("Rows affected: {}", rowSet.rowCount());
    } else {
      LOG.info("Rows unaffected");
    }

    QueryResult queryResult = new QueryResult();
    queryResult.setRows(jsonArray);
    queryResult.setTotalCount(rowSet.rowCount());
    queryResult.setHasMore(false);
    queryResult.setRowsAffected(rowsAffected);
    return queryResult;
  }

  /**
   * Adds a parameter to the SQL Tuple, restoring types that were lost during service proxy JSON
   * serialization.
   *
   * <p>Detection order: UUID → OffsetDateTime → LocalDateTime → fallback (keep original).
   *
   * @param tuple the Tuple to add the parameter to
   * @param param the parameter value (may be a String-encoded UUID, LocalDateTime, etc.)
   */
  private static void addToTuple(Tuple tuple, Object param) {
    if (param == null) {
      tuple.addValue(null);
      return;
    }
    if (param instanceof String paramStr) {
      // 1. Try OffsetDateTime (contains +/- offset or Z suffix)
      if (looksLikeOffsetDateTime(paramStr)) {
        try {
          tuple.addValue(OffsetDateTime.parse(paramStr));
          return;
        } catch (Exception e) {
          LOG.debug("Failed to parse OffsetDateTime, trying LocalDateTime: {}", paramStr);
        }
      }
      // 2. Try LocalDateTime (ISO format without offset)
      if (looksLikeLocalDateTime(paramStr)) {
        try {
          tuple.addValue(LocalDateTime.parse(paramStr));
          return;
        } catch (Exception e) {
          LOG.debug("Failed to parse LocalDateTime, keeping as string: {}", paramStr);
        }
      }
    }
    // 3. Default: keep original type (String, Integer, Long, Double, Boolean, etc.)
    tuple.addValue(param);
  }

  /**
   * Quick check if a string looks like an OffsetDateTime (has timezone offset or Z suffix). Avoids
   * expensive parse attempts for obviously non-datetime strings.
   */
  private static boolean looksLikeOffsetDateTime(String s) {
    if (s.length() < 20) return false; // minimum: 2025-01-01T00:00:00Z
    // Must start with yyyy-MM-ddT pattern
    if (s.charAt(4) != '-' || s.charAt(10) != 'T') return false;
    // Must end with Z, +HH:MM, or -HH:MM
    char last = s.charAt(s.length() - 1);
    if (last == 'Z' || last == 'z') return true;
    // Check for offset pattern at end: +05:30 or -05:30
    int len = s.length();
    return len >= 25
        && (s.charAt(len - 6) == '+' || s.charAt(len - 6) == '-')
        && s.charAt(len - 3) == ':';
  }

  /**
   * Quick check if a string looks like a LocalDateTime. Avoids expensive parse attempts for
   * obviously non-datetime strings.
   */
  private static boolean looksLikeLocalDateTime(String s) {
    // Minimum: 2025-01-01T00:00 (16 chars)
    if (s.length() < 16) return false;
    return s.charAt(4) == '-' && s.charAt(7) == '-' && s.charAt(10) == 'T';
  }

  private Future<QueryResult> executeQuery(String sql, List<Object> params) {
    String formattedParams =
        IntStream.range(0, params.size())
            .mapToObj(i -> "param" + (i + 1) + "=" + params.get(i))
            .collect(Collectors.joining(", "));

    LOG.info("Executing SQL: {} | With parameters: {}", sql, formattedParams);
    Tuple tuple = Tuple.tuple();

    try {
      for (Object param : params) {
        addToTuple(tuple, param);
      }

      return client
          .preparedQuery(sql)
          .execute(tuple)
          .map(
              rowSet -> {
                LOG.info("Query executed successfully.");
                return convertToQueryResult(rowSet);
              })
          .recover(
              err -> {
                LOG.error("SQL execution error: {}", err.getMessage(), err);
                return Future.failedFuture(DxPgExceptionMapper.from(err));
              });

    } catch (Exception e) {
      LOG.error("Exception while building Tuple or executing query: {}", e.getMessage(), e);
      return Future.failedFuture(DxPgExceptionMapper.from(e));
    }
  }

  @Override
  public Future<QueryResult> insert(InsertQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> update(UpdateQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> delete(DeleteQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> select(SelectQuery query, boolean isCountQueryEnabled) {
    LOG.info("Executing select query: {}", query.toSQL());
    String sql = query.toSQL();
    if (isCountQueryEnabled) {
      int selectIndex = sql.toLowerCase().indexOf("select") + 6;
      sql =
          sql.substring(0, selectIndex)
              + " COUNT(*) OVER() AS total_result_count,"
              + sql.substring(selectIndex);
    }
    return executeQuery(sql, query.getQueryParams())
        .map(
            result -> {
              if (isCountQueryEnabled && !result.getRows().isEmpty()) {
                int totalCount =
                    result.getRows().getJsonObject(0).getInteger("total_result_count", 0);
                result.setTotalCount(totalCount);
              }
              return result;
            });
  }

  @Override
  public Future<Boolean> ping() {
    Promise<Boolean> promise = Promise.promise();
    client
        .query("SELECT 1")
        .execute(
            ar -> {
              if (ar.succeeded()) {
                promise.complete(true);
              } else {
                LOG.warn("Ping failed: {}", ar.cause().getMessage());
                promise.complete(false);
              }
            });
    return promise.future();
  }

  @Override
  public Future<QueryResult> upsert(UpsertQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> executeQuery(String sql, JsonArray params) {
    Tuple tuple = Tuple.tuple();
    for (Object value : params) {
      addToTuple(tuple, value);
    }
    return client
        .preparedQuery(sql)
        .execute(tuple)
        .map(this::convertToQueryResult)
        .recover(err -> Future.failedFuture(DxPgExceptionMapper.from(err)));
  }
}
