package org.cdpg.dx.testutil;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.verticle.PostgresVerticle;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for PostgreSQL integration tests using Testcontainers.
 *
 * <p>Starts a PostgreSQL 15 container, runs Flyway migrations, deploys the
 * {@link PostgresVerticle}, and exposes a {@link PostgresService} proxy.
 * Subclasses override hooks to configure migrations, schema, and truncation.
 */
@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresTestBase {

  @Container
  protected static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("testdb")
          .withUsername("postgres")
          .withPassword("testpass");

  protected PostgresService postgresService;
  private String deploymentId;

  /** Override to provide custom Flyway migration locations. */
  protected String[] getMigrationLocations() {
    return new String[] {"classpath:db/migration"};
  }

  /** Override to provide custom schema names for search_path. */
  protected String getSchemaNames() {
    return "public";
  }

  /** Override to provide a TRUNCATE SQL statement run before each test. Return null to skip. */
  protected String getTruncateSQL() {
    return null;
  }

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    // 1. Run Flyway migrations via JDBC (synchronous, reliable)
    Flyway flyway =
        Flyway.configure()
            .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
            .locations(getMigrationLocations())
            .schemas("public")
            .load();
    flyway.migrate();

    // 2. Deploy PostgresVerticle — the Vert.x reactive PG pool connects lazily
    JsonObject pgConfig =
        new JsonObject()
            .put("databaseIP", PG.getHost())
            .put("databasePort", PG.getMappedPort(5432))
            .put("databaseName", PG.getDatabaseName())
            .put("databaseUserName", PG.getUsername())
            .put("databasePassword", PG.getPassword())
            .put("databaseSchema", getSchemaNames())
            .put("poolSize", 5);

    DeploymentOptions opts = new DeploymentOptions().setConfig(pgConfig);

    vertx
        .deployVerticle(new PostgresVerticle(), opts)
        .onComplete(
            ctx.succeeding(
                id -> {
                  deploymentId = id;
                  postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
                  ctx.completeNow();
                }));

    ctx.awaitCompletion(30, TimeUnit.SECONDS);
  }

  @BeforeEach
  void truncateTables(VertxTestContext ctx) {
    String truncateSql = getTruncateSQL();
    if (truncateSql == null || truncateSql.isBlank()) {
      ctx.completeNow();
      return;
    }
    postgresService
        .executeQuery(truncateSql, new JsonArray())
        .onComplete(ctx.succeeding(result -> ctx.completeNow()));
  }

  @AfterAll
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    if (deploymentId != null) {
      vertx.undeploy(deploymentId).onComplete(ctx.succeeding(v -> ctx.completeNow()));
    } else {
      ctx.completeNow();
    }
  }
}
