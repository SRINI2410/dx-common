package org.cdpg.dx.database.elastic.model;

/**
 * Represents the type of filter clause in an Elasticsearch bool query.
 *
 * <p>Used by {@link ElasticsearchQueryDecorator} implementations to specify
 * where their query models should be placed in the final bool query.
 */
public enum FilterType {
  /** Filter context (no scoring). */
  FILTER,
  /** Must match (scoring). */
  MUST,
  /** Must not match. */
  MUST_NOT,
  /** Should match (optional). */
  SHOULD,
  /** Source field includes. */
  INCLUDES
}
