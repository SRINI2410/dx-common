package org.cdpg.dx.deploy;

import com.hazelcast.config.Config;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.zookeeper.ZookeeperDiscoveryProperties;
import com.hazelcast.zookeeper.ZookeeperDiscoveryStrategyFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.TypedOption;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.cdpg.dx.deploy.util.ConfigHelper;

/**
 * Base deployer with common CLI parsing, clustered/non-clustered deployment, metrics, and graceful
 * shutdown. Subclasses provide the application name and config-merging strategy via {@link
 * #launch(String[], String, UnaryOperator)}.
 */
public class BaseDeployer {
  private static final Logger LOGGER = LogManager.getLogger(BaseDeployer.class);
  private static Vertx vertxInstance;

  /**
   * Entry point for base project Deployers. Parses CLI args and deploys accordingly.
   *
   * @param args CLI arguments
   * @param appName application name for the CLI
   * @param configMerger function to merge required configs into root config (typically {@code
   *     ConfigHelper::mergeRequiredConfigs})
   */
  public static void launch(String[] args, String appName, UnaryOperator<JsonObject> configMerger) {
    CLI cli =
        CLI.create(appName)
            .setSummary("A CLI to deploy the " + appName)
            .addOption(
                new Option()
                    .setLongName("help")
                    .setShortName("h")
                    .setFlag(true)
                    .setDescription("display help"))
            .addOption(
                new Option()
                    .setLongName("config")
                    .setShortName("c")
                    .setRequired(true)
                    .setDescription("configuration file"))
            .addOption(
                new Option()
                    .setLongName("isClustered")
                    .setShortName("C")
                    .setRequired(true)
                    .setDefaultValue("false")
                    .setDescription("Is it being deployed in clustered mode"))
            .addOption(
                new Option()
                    .setLongName("host")
                    .setShortName("i")
                    .setRequired(true)
                    .setDefaultValue("localhost")
                    .setDescription("public host"))
            .addOption(
                new TypedOption<String>()
                    .setType(String.class)
                    .setLongName("modules")
                    .setShortName("m")
                    .setRequired(false)
                    .setDefaultValue("all")
                    .setParsedAsList(true)
                    .setDescription(
                        "comma separated list of verticle names to deploy. "
                            + "If omitted, or if `all` is passed, all verticles are deployed"));

    StringBuilder usageString = new StringBuilder();
    cli.usage(usageString);
    CommandLine commandLine = cli.parse(Arrays.asList(args), false);
    boolean isDeploymentInClusteredMode =
        Boolean.parseBoolean(commandLine.getOptionValue("isClustered"));

    if (commandLine.isValid() && !commandLine.isFlagEnabled("help")) {
      String configPath = commandLine.getOptionValue("config");
      String host = commandLine.getOptionValue("host");
      List<String> passedModules = commandLine.getOptionValues("modules");
      List<String> modules = passedModules.stream().distinct().collect(Collectors.toList());
      if (!isDeploymentInClusteredMode) {
        deploy(configPath, configMerger);
      } else if (modules.contains("all")) {
        deployInClusteredMode(configPath, host, List.of(), configMerger);
      } else {
        deployInClusteredMode(configPath, host, modules, configMerger);
      }

      Runtime.getRuntime().addShutdownHook(new Thread(BaseDeployer::gracefulShutdown));
    } else {
      LOGGER.info(usageString);
    }
  }

  public static void recursiveDeploy(
      Vertx vertx, JsonObject configs, int i, UnaryOperator<JsonObject> configMerger) {
    JsonObject mergedConfigs = configMerger.apply(configs);
    if (i >= mergedConfigs.getJsonArray("modules").size()) {
      LOGGER.info("Deployed all");
      return;
    }

    JsonObject moduleConfigurations = getConfigForModule(i, mergedConfigs);
    String moduleName = moduleConfigurations.getString("id");
    int numInstances = moduleConfigurations.getInteger("verticleInstances");
    DeploymentOptions deploymentOptions =
        new DeploymentOptions().setInstances(numInstances).setConfig(moduleConfigurations);

    boolean isWorkerVerticle = moduleConfigurations.getBoolean("isWorkerVerticle", false);
    if (isWorkerVerticle) {
      LOGGER.info("worker verticle : {}", moduleConfigurations.getString("id"));
      deploymentOptions.setWorkerPoolName(moduleConfigurations.getString("threadPoolName"));
      deploymentOptions.setWorkerPoolSize(moduleConfigurations.getInteger("threadPoolSize"));
      deploymentOptions.setThreadingModel(ThreadingModel.WORKER);
      deploymentOptions.setMaxWorkerExecuteTime(30L);
      deploymentOptions.setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES);
    }

    vertx.deployVerticle(
        moduleName,
        deploymentOptions,
        ar -> {
          if (ar.succeeded()) {
            LOGGER.info("Deployed {}", moduleName);
            recursiveDeploy(vertx, mergedConfigs, i + 1, configMerger);
          } else {
            LOGGER.fatal("Failed to deploy {} cause:", moduleName, ar.cause());
          }
        });
  }

  public static void recursiveDeploy(
      Vertx vertx,
      JsonObject configs,
      List<String> modules,
      UnaryOperator<JsonObject> configMerger) {
    if (modules.isEmpty()) {
      LOGGER.info("Deployed requested verticles");
      return;
    }
    JsonArray configuredModules = configMerger.apply(configs).getJsonArray("modules");

    String moduleName = modules.get(0);
    JsonObject config =
        configuredModules.stream()
            .map(obj -> (JsonObject) obj)
            .filter(obj -> obj.getString("id").equals(moduleName))
            .findFirst()
            .orElse(new JsonObject());

    if (config.isEmpty()) {
      LOGGER.fatal("Failed to deploy {} | cause : Not Found", moduleName);
      return;
    }
    int numInstances = config.getInteger("verticleInstances");
    DeploymentOptions deploymentOptions =
        new DeploymentOptions().setInstances(numInstances).setConfig(config);
    boolean isWorkerVerticle = config.getBoolean("isWorkerVerticle", false);
    if (isWorkerVerticle) {
      LOGGER.info("worker verticle : {}", config.getString("id"));
      deploymentOptions.setWorkerPoolName(config.getString("threadPoolName"));
      deploymentOptions.setWorkerPoolSize(config.getInteger("threadPoolSize"));
      deploymentOptions.setThreadingModel(ThreadingModel.WORKER);
      deploymentOptions.setMaxWorkerExecuteTime(30L);
      deploymentOptions.setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES);
    }

    vertx.deployVerticle(
        moduleName,
        deploymentOptions,
        ar -> {
          if (ar.succeeded()) {
            LOGGER.info("Deployed {}", moduleName);
            modules.remove(0);
            recursiveDeploy(vertx, configs, modules, configMerger);
          } else {
            LOGGER.fatal("Failed to deploy {} cause:", moduleName, ar.cause());
          }
        });
  }

  private static JsonObject getConfigForModule(int moduleIndex, JsonObject configurations) {
    return configurations.getJsonArray("modules").getJsonObject(moduleIndex);
  }

  public static void deploy(String configPath, UnaryOperator<JsonObject> configMerger) {
    EventBusOptions ebOptions = new EventBusOptions();
    VertxOptions options =
        new VertxOptions().setEventBusOptions(ebOptions).setMetricsOptions(getMetricsOptions());

    String config;
    try {
      config = new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    if (config.isEmpty()) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    JsonObject configuration = new JsonObject(config);
    Vertx vertx = Vertx.vertx(options);
    setVertxInstance(vertx);
    setJvmMetrics();

    recursiveDeploy(vertx, configuration, 0, configMerger);
  }

  private static void setVertxInstance(Vertx vertx) {
    vertxInstance = vertx;
  }

  public static void deployInClusteredMode(
      String configPath,
      String host,
      List<String> modules,
      UnaryOperator<JsonObject> configMerger) {
    String config;
    try {
      config = new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    if (config.isEmpty()) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    JsonObject configuration = new JsonObject(config);
    List<String> zookeepers = configuration.getJsonArray("zookeepers").getList();
    String clusterId = configuration.getString("clusterId");
    ClusterManager mgr = getClusterManager(host, zookeepers, clusterId);
    EventBusOptions ebOptions = new EventBusOptions().setClusterPublicHost(host);
    VertxOptions options =
        new VertxOptions().setEventBusOptions(ebOptions).setMetricsOptions(getMetricsOptions());
    LOGGER.debug("metrics-options {}", options.getMetricsOptions());
    Vertx.builder()
        .withClusterManager(mgr)
        .with(options)
        .buildClustered()
        .onSuccess(
            vertx -> {
              setVertxInstance(vertx);
              LOGGER.debug(vertxInstance.isMetricsEnabled());
              setJvmMetrics();
              if (modules.isEmpty()) {
                recursiveDeploy(vertxInstance, configuration, 0, configMerger);
              } else {
                recursiveDeploy(vertxInstance, configuration, modules, configMerger);
              }
            })
        .onFailure(throwable -> LOGGER.fatal("Could not join cluster"));
  }

  public static ClusterManager getClusterManager(
      String host, List<String> zookeepers, String clusterId) {
    Config config = new Config();
    config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    config.getNetworkConfig().setPublicAddress(host);
    config.setProperty("hazelcast.discovery.enabled", "true");
    config.setProperty("hazelcast.logging.type", "log4j2");
    DiscoveryStrategyConfig discoveryStrategyConfig =
        new DiscoveryStrategyConfig(new ZookeeperDiscoveryStrategyFactory());
    discoveryStrategyConfig.addProperty(
        ZookeeperDiscoveryProperties.ZOOKEEPER_URL.key(), String.join(",", zookeepers));
    discoveryStrategyConfig.addProperty(ZookeeperDiscoveryProperties.GROUP.key(), clusterId);
    config
        .getNetworkConfig()
        .getJoin()
        .getDiscoveryConfig()
        .addDiscoveryStrategyConfig(discoveryStrategyConfig);

    return new HazelcastClusterManager(config);
  }

  public static MetricsOptions getMetricsOptions() {
    return new MicrometerMetricsOptions()
        .setPrometheusOptions(
            new VertxPrometheusOptions()
                .setEnabled(true)
                .setStartEmbeddedServer(true)
                .setEmbeddedServerOptions(new HttpServerOptions().setPort(9000)))
        .setLabels(
            EnumSet.of(Label.EB_ADDRESS, Label.EB_FAILURE, Label.HTTP_CODE, Label.HTTP_METHOD))
        .setEnabled(true);
  }

  public static void setJvmMetrics() {
    LOGGER.debug("Setting JVM metrics");
    MeterRegistry registry = BackendRegistries.getDefaultNow();
    LOGGER.debug(registry);
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
  }

  public static void gracefulShutdown() {
    Set<String> deployIdSet = vertxInstance.deploymentIDs();
    LOGGER.info("Shutting down the application");
    CountDownLatch latchVerticles = new CountDownLatch(deployIdSet.size());
    CountDownLatch latchCluster = new CountDownLatch(1);
    CountDownLatch latchVertx = new CountDownLatch(1);
    LOGGER.debug("number of verticles being undeployed are: {}", deployIdSet.size());
    for (String deploymentId : deployIdSet) {
      vertxInstance.undeploy(
          deploymentId,
          handler -> {
            if (handler.succeeded()) {
              LOGGER.debug("{} verticle successfully Undeployed", deploymentId);
              latchVerticles.countDown();
            } else {
              LOGGER.warn("{} Undeploy failed!", deploymentId);
            }
          });
    }

    try {
      latchVerticles.await(5, TimeUnit.SECONDS);
      LOGGER.info("All the verticles undeployed");
      return;
    } catch (Exception e) {
      LOGGER.error("Error during verticle shutdown", e);
    }

    try {
      latchCluster.await(5, TimeUnit.SECONDS);
      vertxInstance.close(
          handler -> {
            if (handler.succeeded()) {
              LOGGER.info("vertx closed successfully");
              latchVertx.countDown();
            } else {
              LOGGER.warn("Vertx didn't close properly, reason:", handler.cause());
            }
          });
    } catch (Exception e) {
      LOGGER.error("Error during cluster shutdown", e);
    }

    try {
      latchVertx.await(5, TimeUnit.SECONDS);
      if (LogManager.getContext() instanceof LoggerContext) {
        LOGGER.debug("Shutting down log4j2");
        LogManager.shutdown(LogManager.getContext());
      } else {
        LOGGER.warn("Unable to shutdown log4j2");
      }
    } catch (Exception e) {
      LOGGER.error("Error during vertx shutdown", e);
    }
  }
}
