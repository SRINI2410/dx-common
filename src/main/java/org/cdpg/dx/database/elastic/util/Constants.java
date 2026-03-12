package org.cdpg.dx.database.elastic.util;

/**
 * Shared Elasticsearch constants used by common database code.
 *
 * <p>This file contains only the constants that are shared across projects
 * (dx-controlplane, dx-dataplane-rs). Project-specific constants should
 * remain in the respective project's own Constants file.
 */
public class Constants {

  /* Database connection config keys */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_UNAME = "databaseUser";
  public static final String DATABASE_PASSWD = "databasePassword";

  /* General Purpose */
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String SOURCE = "_source";
  public static final String TYPE = "type";

  /* Query parameter keys */
  public static final String FIELD = "field";
  public static final String VALUE = "value";
  public static final String FUZZY = "fuzzy";
  public static final String OPERATOR = "operator";
  public static final String Q_VALUE = "q";
  public static final String CASE_INSENSITIVE = "case_insensitive";

  /* Search option modes */
  public static final String DOC_IDS_ONLY = "DOCIDS";
  public static final String SOURCE_ONLY = "SOURCE";
  public static final String SOURCE_AND_ID = "SOURCE_ID";
  public static final String SOURCE_AND_ID_GEOQUERY = "SOURCE_ID_GEOQUERY";
  public static final String AGGREGATION_ONLY = "AGGREGATION";
  public static final String AGGREGATION_LIST = "AGGREGATION_LIST";
  public static final String COUNT_AGGREGATION_ONLY = "COUNT_AGGREGATION";

  /* Aggregation response keys */
  public static final String AGGREGATIONS = "aggregations";
  public static final String BUCKETS = "buckets";
  public static final String DOC_COUNT = "doc_count";
  public static final String RESULTS = "results";

  /* Special document keys */
  public static final String SUMMARY_KEY = "_summary";
  public static final String WORD_VECTOR_KEY = "_word_vector";

  /* Geo-Spatial */
  public static final String GEO_CIRCLE = "Circle";
  public static final String GEO_PROPERTY = "geoproperty";
  public static final String COORDINATES = "coordinates";

  /* Range query keys */
  public static final String GREATER_THAN_EQUALS = "gte";
  public static final String LESS_THAN_EQUALS = "lte";
  public static final String GREATER_THAN = "gt";
  public static final String LESS_THAN = "lt";

  /* Verticle configuration */
  public static final String SERVICE_ADDRESS_KEY = "serviceAddress";

  /* Size/pagination */
  public static final String SIZE_KEY = "size";
  public static final int STRING_SIZE = 100;
}
