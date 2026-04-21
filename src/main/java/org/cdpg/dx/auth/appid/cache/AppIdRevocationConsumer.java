package org.cdpg.dx.auth.appid.cache;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Consumes AppId revocation messages from RabbitMQ and directly invalidates
 * the in-process caches.
 *
 * <p>Binds to the {@code revoked-appid} exchange (topic, routing key {@code #}) via a queue
 * of the same name. On each message, calls {@code invalidate(appId)} on both caches.
 *
 * <p>Expected message format: {@code { "appId": "<the-revoked-app-id>" }}
 */
public class AppIdRevocationConsumer {

  private static final Logger LOGGER = LogManager.getLogger(AppIdRevocationConsumer.class);

  private static final QueueOptions QUEUE_OPTIONS =
      new QueueOptions().setMaxInternalQueueSize(1000).setKeepMostRecent(true);

  private final RabbitMQClient rabbitMQClient;
  private final AppIdCacheService appIdCacheService;
  private final AppIdItemAccessCacheService itemAccessCacheService;
  private final String queueName;

  public AppIdRevocationConsumer(
      RabbitMQClient rabbitMQClient,
      AppIdCacheService appIdCacheService,
      AppIdItemAccessCacheService itemAccessCacheService,
      String queueName) {
    this.rabbitMQClient = rabbitMQClient;
    this.appIdCacheService = appIdCacheService;
    this.itemAccessCacheService = itemAccessCacheService;
    this.queueName = queueName;
  }

  public Future<Void> start() {
    return rabbitMQClient
        .basicConsumer(queueName, QUEUE_OPTIONS)
        .onSuccess(consumer -> {
          LOGGER.info("AppIdRevocationConsumer started, listening on queue={}", queueName);
          consumer.handler(message -> {
            Buffer body = message.body();
            if (body == null || body.length() == 0) {
              LOGGER.warn("Empty message received on revocation queue={}", queueName);
              return;
            }
            try {
              JsonObject payload = new JsonObject(body);
              String appId = payload.getString("appId");
              if (appId == null || appId.isBlank()) {
                LOGGER.warn("Missing or blank appId in revocation message: {}", payload);
                return;
              }
              LOGGER.info("Revoking cache for appId={}", appId);
              appIdCacheService.invalidate(appId);
              itemAccessCacheService.invalidate(appId);
            } catch (Exception e) {
              LOGGER.error("Failed to process revocation message: {}", e.getMessage());
            }
          });
        })
        .onFailure(err -> LOGGER.error("Failed to start AppIdRevocationConsumer: {}", err.getMessage()))
        .mapEmpty();
  }
}
