package org.cdpg.dx.testutil;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.ElasticsearchVerticle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ElasticsearchTestBase {

  @Container
  protected static final ElasticsearchContainer ES =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.0")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  protected ElasticsearchService elasticsearchService;
  private String deploymentId;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    JsonObject esConfig =
        new JsonObject()
            .put("databaseIP", ES.getHost())
            .put("databasePort", ES.getMappedPort(9200))
            .put("databaseUser", "")
            .put("databasePassword", "");

    DeploymentOptions opts = new DeploymentOptions().setConfig(esConfig);

    vertx
        .deployVerticle(new ElasticsearchVerticle(), opts)
        .onComplete(
            ctx.succeeding(
                id -> {
                  deploymentId = id;
                  elasticsearchService =
                      ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
                  ctx.completeNow();
                }));

    ctx.awaitCompletion(60, TimeUnit.SECONDS);
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
