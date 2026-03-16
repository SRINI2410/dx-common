package org.cdpg.dx.database.elastic.model;

import java.util.List;
import java.util.Map;

/**
 * Decorator interface for composing Elasticsearch queries.
 *
 * <p>Implementations add their specific query clauses to the provided
 * {@code queryMap} and return it. This enables a chain-of-decorators
 * pattern for building complex bool queries.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * Map<FilterType, List<QueryModel>> queryMap = new EnumMap<>(FilterType.class);
 * queryMap = new TextSearchQueryDecorator(queryMap, textDto).add();
 * queryMap = new InstanceFilterQueryDecorator(queryMap, instanceDto).add();
 * }</pre>
 */
public interface ElasticsearchQueryDecorator {
  Map<FilterType, List<QueryModel>> add();
}
