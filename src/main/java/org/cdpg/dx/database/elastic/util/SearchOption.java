package org.cdpg.dx.database.elastic.util;

/**
 * Defines how search results should be formatted.
 *
 * <p>Replaces the previously stringly-typed {@code String options} parameter in {@link
 * org.cdpg.dx.database.elastic.service.ElasticsearchService#search}.
 */
public enum SearchOption {
  /** Return only document IDs (no source). */
  DOC_IDS_ONLY("DOCIDS"),

  /** Return source fields only (strips _summary and _word_vector). */
  SOURCE_ONLY("SOURCE"),

  /** Return both document ID and source as separate fields. */
  SOURCE_AND_ID("SOURCE_ID"),

  /** Return source with doc_id merged into the source object (for geo queries). */
  SOURCE_AND_ID_GEOQUERY("SOURCE_ID_GEOQUERY"),

  /** Return aggregations only (size=0, no hits). */
  AGGREGATION_ONLY("AGGREGATION"),

  /** Return aggregation bucket keys as lists. */
  AGGREGATION_LIST("AGGREGATION_LIST"),

  /** Return aggregation bucket key-to-doc_count mappings. */
  COUNT_AGGREGATION_ONLY("COUNT_AGGREGATION"),

  /** Default: return raw source without special processing. */
  DEFAULT("DEFAULT");

  private final String value;

  SearchOption(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Parse a legacy string option into a SearchOption.
   *
   * @param options the string to parse (may be null)
   * @return the matching SearchOption, or DEFAULT if no match
   */
  public static SearchOption fromString(String options) {
    if (options == null) {
      return DEFAULT;
    }
    for (SearchOption opt : values()) {
      if (opt.value.equals(options)) {
        return opt;
      }
    }
    // Legacy support: startsWith check for AGGREGATION variants
    if (options.startsWith("AGGREGATION")) {
      return AGGREGATION_ONLY;
    }
    return DEFAULT;
  }
}
