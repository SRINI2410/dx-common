package org.cdpg.dx.testutil;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.REDIS_SERVICE_ADDRESS;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.database.redis.service.RedisService;
import org.cdpg.dx.database.redis.verticle.RedisVerticle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RedisTestBase {

  @Container
  protected static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
          .withExposedPorts(6379);

  protected RedisService redisService;
  private String deploymentId;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    JsonObject redisConfig =
        new JsonObject()
            .put("redisHost", REDIS.getHost())
            .put("redisPort", REDIS.getMappedPort(6379))
            .put("redisUsername", "default")
            .put("redisPassword", "");

    DeploymentOptions opts = new DeploymentOptions().setConfig(redisConfig);

    vertx
        .deployVerticle(new RedisVerticle(), opts)
        .onComplete(
            ctx.succeeding(
                id -> {
                  deploymentId = id;
                  redisService = RedisService.createProxy(vertx, REDIS_SERVICE_ADDRESS);
                  ctx.completeNow();
                }));

    ctx.awaitCompletion(30, TimeUnit.SECONDS);
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
