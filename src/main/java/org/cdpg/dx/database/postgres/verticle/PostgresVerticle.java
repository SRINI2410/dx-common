package org.cdpg.dx.database.postgres.verticle;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;
import static org.cdpg.dx.database.postgres.util.Constants.DB_RECONNECT_ATTEMPTS;
import static org.cdpg.dx.database.postgres.util.Constants.DB_RECONNECT_INTERVAL_MS;
import static org.cdpg.dx.database.postgres.util.Constants.SERVICE_ADDRESS_KEY;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.service.PostgresServiceImpl;

import java.util.Map;

/**
 * Generic PostgreSQL Verticle.
 *
 * <p>Exposes a {@link PostgresService} over the Vert.x Event Bus. The EventBus address is
 * configurable via the {@code "serviceAddress"} config key, defaulting to {@link
 * org.cdpg.dx.common.config.ServiceProxyAddressConstants#POSTGRES_SERVICE_ADDRESS}.
 *
 * <p>This allows deploying <b>multiple instances</b> of this verticle pointing to different
 * PostgreSQL databases simply by providing different configuration blocks.
 */
public class PostgresVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(PostgresVerticle.class);
  private MessageConsumer<JsonObject> consumer;
  private ServiceBinder binder;
  private Pool pool;

  @Override
  public void start(Promise<Void> startPromise) {
    /* Database Properties */
    String databaseIp = config().getString("databaseIP");
    int databasePort = config().getInteger("databasePort");
    String databaseSchema = config().getString("databaseSchema");
    String databaseName = config().getString("databaseName");
    String databaseUserName = config().getString("databaseUserName");
    String databasePassword = config().getString("databasePassword");
    int poolSize = config().getInteger("poolSize");
    Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

    /* Set Connection Object and schema */
    PgConnectOptions connectOptions =
        new PgConnectOptions()
            .setPort(databasePort)
            .setHost(databaseIp)
            .setProperties(schemaProp)
            .setDatabase(databaseName)
            .setUser(databaseUserName)
            .setPassword(databasePassword)
            .setReconnectAttempts(DB_RECONNECT_ATTEMPTS)
            .setReconnectInterval(DB_RECONNECT_INTERVAL_MS);

    /* Pool options */
    PoolOptions poolOptions = new PoolOptions().setMaxSize(poolSize);

    /* Create the client pool */
    this.pool = Pool.pool(vertx, connectOptions, poolOptions);
    PostgresService service = new PostgresServiceImpl(pool);
    binder = new ServiceBinder(vertx);

    // Configurable EventBus address — defaults to POSTGRES_SERVICE_ADDRESS
    String address = config().getString(SERVICE_ADDRESS_KEY, POSTGRES_SERVICE_ADDRESS);
    consumer = binder.setAddress(address).register(PostgresService.class, service);

    LOGGER.info("Postgres service registered at address: {} ({}:{})", address, databaseIp, databasePort);
    startPromise.complete();
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
    pool.close();
  }
}
