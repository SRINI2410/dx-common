package org.cdpg.dx.database.elastic;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.service.ElasticsearchServiceImpl;

/**
 * Generic Elasticsearch Verticle.
 *
 * <p>Exposes an {@link ElasticsearchService} over the Vert.x Event Bus. The EventBus address is
 * configurable via the {@code "serviceAddress"} config key, defaulting to {@link
 * org.cdpg.dx.common.config.ServiceProxyAddressConstants#ELASTIC_SERVICE_ADDRESS}.
 *
 * <p>This allows deploying <b>multiple instances</b> of this verticle pointing to different
 * Elasticsearch clusters simply by providing different configuration blocks:
 *
 * <pre>{@code
 * // Primary ES cluster (default address)
 * { "id": "org.cdpg.dx.database.elastic.ElasticsearchVerticle",
 *   "databaseIP": "local-es", "databasePort": 9200, ... }
 *
 * // Secondary ES cluster (custom address)
 * { "id": "org.cdpg.dx.database.elastic.ElasticsearchVerticle",
 *   "serviceAddress": "org.cdpg.dx.database.elastic.central.service",
 *   "databaseIP": "central-es", "databasePort": 9200, ... }
 * }</pre>
 *
 * @version 2.0
 * @since 2020-05-31
 */
public class ElasticsearchVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(ElasticsearchVerticle.class);

  private ElasticsearchService database;
  private String databaseIp;
  private String databaseUser;
  private String databasePassword;
  private int databasePort;
  private ElasticClient client;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;

  @Override
  public void start() throws Exception {
    binder = new ServiceBinder(vertx);
    databaseIp = config().getString(DATABASE_IP);
    databasePort = config().getInteger(DATABASE_PORT);
    databaseUser = config().getString(DATABASE_UNAME);
    databasePassword = config().getString(DATABASE_PASSWD);

    // Configurable EventBus address — defaults to ELASTIC_SERVICE_ADDRESS
    String address = config().getString(SERVICE_ADDRESS_KEY, ELASTIC_SERVICE_ADDRESS);

    client = new ElasticClient(databaseIp, databasePort, databaseUser, databasePassword);
    database = new ElasticsearchServiceImpl(client);

    consumer = binder.setAddress(address).register(ElasticsearchService.class, database);

    LOGGER.info(
        "Elasticsearch service registered at address: {} ({}:{})", address, databaseIp, databasePort);
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
