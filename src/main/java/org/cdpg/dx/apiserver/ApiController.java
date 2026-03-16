package org.cdpg.dx.apiserver;

import io.vertx.ext.web.openapi.RouterBuilder;

/**
 * Interface for all OpenAPI-driven API controllers across DX microservices.
 *
 * <p>Implementations bind their route handlers to OpenAPI operation IDs
 * via {@link RouterBuilder#operation(String)}.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * public class MyController implements ApiController {
 *   @Override
 *   public void register(RouterBuilder builder) {
 *     builder.operation("listItems").handler(this::handleListItems);
 *   }
 * }
 * }</pre>
 */
public interface ApiController {
  void register(RouterBuilder builder);
}
