package org.cdpg.dx.database.elastic.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Shared Elasticsearch constants used by common database code.
 *
 * <p>This file contains constants that are shared across projects
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
  public static final String SEARCH_TYPE = "searchType";
  public static final String PROD_INSTANCE = "production";
  public static final String TEST_INSTANCE = "test";

  /* Query parameter keys */
  public static final String FIELD = "field";
  public static final String VALUE = "value";
  public static final String FUZZY = "fuzzy";
  public static final String OPERATOR = "operator";
  public static final String Q_VALUE = "q";
  public static final String CASE_INSENSITIVE = "case_insensitive";

  /* Attribute query keys */
  public static final String ATTRIBUTE_QUERY_KEY = "attr-query";
  public static final String ATTRIBUTE_KEY = "attribute";
  public static final String VALUE_LOWER = "valueLower";
  public static final String VALUE_UPPER = "valueUpper";
  public static final String GREATER_THAN_OP = ">";
  public static final String LESS_THAN_OP = "<";
  public static final String GREATER_THAN_EQ_OP = ">=";
  public static final String LESS_THAN_EQ_OP = "<=";
  public static final String EQUAL_OP = "==";
  public static final String NOT_EQUAL_OP = "!=";
  public static final String BETWEEN_OP = "<==>";

  public static final String DATA_SAMPLE = "dataSample";
  public static final String DATA_DESCRIPTOR = "dataDescriptor";
  public static final String LABEL = "label";
  public static final String DOC_ID = "_id";
  public static final String ERROR_INVALID_PARAMETER = "Incorrect/missing query parameters";

  /* Geo-Spatial */
  public static final String LAT = "lat";
  public static final String LON = "lon";
  public static final String GEOMETRY = "geometry";
  public static final String GEOREL = "georel";
  public static final String WITHIN = "within";
  public static final String POLYGON = "polygon";
  public static final String LINESTRING = "linestring";
  public static final String GEO_PROPERTY = "geoproperty";
  public static final String BBOX = "bbox";
  public static final String GEO_RADIUS = "radius";
  public static final String LOCATION = "location";
  public static final String COORDINATES = "coordinates";
  public static final String COORDINATES_KEY = "coordinates";
  public static final String GEO_BBOX = "envelope";
  public static final String GEO_CIRCLE = "Circle";
  public static final String GEO_KEY = ".geometry";
  public static final String MAX_DISTANCE = "maxDistance";
  public static final String POINT = "Point";

  /* GeoRels */
  public static final String GEOREL_WITHIN = "within";
  public static final String GEOREL_NEAR = "near";
  public static final String GEOREL_COVERED_BY = "coveredBy";
  public static final String GEOREL_INTERSECTS = "intersects";
  public static final String GEOREL_EQUALS = "equals";
  public static final String GEOREL_DISJOINT = "disjoint";
  public static final String INTERSECTS = "intersects";
  public static final String GEORELATION = "georel";

  /* Temporal */
  public static final String REQ_TIMEREL = "timerel";
  public static final String TIME_KEY = "time";
  public static final String END_TIME = "endtime";
  public static final String DURING = "during";
  public static final String BETWEEN = "between";
  public static final String AFTER = "after";
  public static final String BEFORE = "before";
  public static final String TEQUALS = "tequals";
  public static final String TIME_LIMIT = "timeLimit";

  /* Search type regex */
  public static final String TAGSEARCH_REGEX = "(.*)tagsSearch(.*)";
  public static final String TEXTSEARCH_REGEX = "(.*)textSearch(.*)";
  public static final String ATTRIBUTE_SEARCH_REGEX = "(.*)attributeSearch(.*)";
  public static final String SEARCH_CRITERIA_REGEX = "(.*)searchCriteria(.*)";
  public static final String GEOSEARCH_REGEX = "(.*)geoSearch(.*)";
  public static final String RESPONSE_FILTER_REGEX = "(.*)responseFilter(.*)";
  public static final String TEMPORAL_SEARCH_REGEX = "(.*)temporalSearch(.*)";

  /* Search option modes */
  public static final String DOC_IDS_ONLY = "DOCIDS";
  public static final String SOURCE_ONLY = "SOURCE";
  public static final String SOURCE_AND_ID = "SOURCE_ID";
  public static final String SOURCE_AND_ID_GEOQUERY = "SOURCE_ID_GEOQUERY";
  public static final String AGGREGATION_ONLY = "AGGREGATION";
  public static final String AGGREGATION_LIST = "AGGREGATION_LIST";
  public static final String COUNT_AGGREGATION_ONLY = "COUNT_AGGREGATION";
  public static final String RATING_AGGREGATION_ONLY = "R_AGGREGATION";
  public static final String RESOURCE_AGGREGATION_ONLY = "RESOURCE_AGGREGATION";
  public static final String PROVIDER_AGGREGATION_ONLY = "PROVIDER_AGGREGATION";
  public static final String DATASET = "DATASET";
  public static final String TYPE_KEYWORD = "type.keyword";

  /* Aggregation response keys */
  public static final String AGGREGATIONS = "aggregations";
  public static final String BUCKETS = "buckets";
  public static final String DOC_COUNT = "doc_count";
  public static final String RESULTS = "results";

  /* Special document keys */
  public static final String SUMMARY_KEY = "_summary";
  public static final String GEOSUMMARY_KEY = "_geosummary";
  public static final String WORD_VECTOR_KEY = "_word_vector";

  /* DB Query related */
  public static final String MATCH_KEY = "match";
  public static final String TERMS_KEY = "terms";
  public static final String STRING_QUERY_KEY = "query_string";
  public static final String FROM = "from";
  public static final String KEYWORD_KEY = ".keyword";
  public static final String DEVICEID_KEY = "deviceId";
  public static final String TAG_AQM = "aqm";
  public static final String DESCRIPTION_ATTR = "description";
  public static final String ACCESS_POLICY = "accessPolicy";
  public static final String OPEN = "OPEN";
  public static final String PRIVATE = "PRIVATE";
  public static final String RESTRICTED = "RESTRICTED";
  public static final String FORWARD_SLASH = "/";
  public static final String WILDCARD_KEY = "wildcard";

  /* Response keys */
  public static final String RESPONSE_ATTRS = "attrs";
  public static final String RESPONSE_FILTER = "responseFilter_";
  public static final String RESPONSE_FILTER_GEO = "responseFilter_geoSearch_";

  /* General purpose */
  public static final String SEARCH = "search";
  public static final String COUNT = "count";
  public static final String ATTRIBUTE = "attrs";
  public static final String RESULT = "results";
  public static final String SIZE_KEY = "size";
  public static final String PAGE_KEY = "page";
  public static final int DEFAULT_SIZE_VALUE = 5000;
  public static final int DEFAULT_FROM_VALUE = 0;

  public static final int STATIC_DELAY_TIME = 3000;
  public static final String FILTER_PATH = "?filter_path=took,hits.total.value,hits.hits._source";
  public static final String FILTER_PATH_AGGREGATION =
      "?filter_path=hits.total.value,aggregations.results.buckets";
  public static final String FILTER_ID_ONLY_PATH =
      "?filter_path=hits.total.value,hits.hits._id&size=10000";
  public static final String FILTER_PATH_ID_AND_SOURCE =
      "?filter_path=took,hits.total.value,hits.hits._source,hits.hits._id";
  public static final String TYPE_KEY = "type";
  public static final String ID_KEYWORD = "id.keyword";

  /* Error */
  public static final String DATABASE_BAD_QUERY = "Query Failed with status != 20x";
  public static final String NO_SEARCH_TYPE_FOUND = "No searchType found";
  public static final String ERROR_DB_REQUEST = "DB request has failed";
  public static final String INSTANCE_NOT_EXISTS = "instance doesn't exist";
  public static final String SHAPE_KEY = "shape";
  public static final String AGGREGATION_KEY = "aggs";
  public static final String GEO_SHAPE_KEY = "geo_shape";

  /* Database config */
  public static final String CONFIG_FILE = "config.properties";
  public static final String OPTIONAL_MODULES = "optionalModules";
  public static final String IS_SSL = "ssl";
  public static final String PORT = "httpPort";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTORE_PASSWORD = "keystorePassword";

  /* Index names */
  public static final String DOC_INDEX = "docIndex";
  public static final String RATING_INDEX = "ratingIndex";
  public static final String MLAYER_INSTANCE_INDEX = "mlayerInstanceIndex";
  public static final String MLAYER_DOMAIN_INDEX = "mlayerDomainIndex";
  public static final String PUBLIC_KEY = "publicKey";

  /* UUID Pattern */
  public static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}$");

  /* Item type */
  public static final String RELATIONSHIP = "relationship";
  public static final String RESOURCE = "resource";
  public static final String RESOURCE_GRP = "resourceGroup";
  public static final String RESOURCE_SVR = "resourceServer";
  public static final String PROVIDER = "provider";
  public static final String PROVIDERS = "providers";
  public static final String ALL = "all";
  public static final String COS = "cos";
  public static final String OWNER = "owner";
  public static final String COS_ADMIN = "cos_admin";
  public static final String ORG_ADMIN = "org_admin";
  public static final String USER = "user";
  public static final String APD_URL = "apdURL";
  public static final String VERIFIED_BY = "verifiedBy";
  public static final String AUTHORIZATION_KEY = "Authorization";
  public static final String BEARER_KEY = "Bearer";
  public static final String HTTPS = "https://";
  public static final String PROVIDER_USER_ID = "ownerUserId";
  public static final String RESOURCE_SERVER_URL = "resourceServerRegURL";
  public static final String COS_ITEM = "cos";
  public static final String RESOURCETYPE = "resourceType";
  public static final String MALFORMED_ID = "Malformed Id ";

  /* Item types */
  public static final String ITEM_TYPE_RESOURCE = "Resource";
  public static final String ITEM_TYPE_RESOURCE_GROUP = "ResourceGroup";
  public static final String ITEM_TYPE_RESOURCE_SERVER = "ResourceServer";
  public static final String ITEM_TYPE_PROVIDER = "Provider";
  public static final String ITEM_TYPE_COS = "COS";
  public static final String ITEM_TYPE_OWNER = "Owner";
  public static final String ITEM_TYPE_INSTANCE = "Instance";

  public static final ArrayList<String> ITEM_TYPES =
      new ArrayList<String>(
          Arrays.asList(
              ITEM_TYPE_RESOURCE,
              ITEM_TYPE_RESOURCE_GROUP,
              ITEM_TYPE_RESOURCE_SERVER,
              ITEM_TYPE_PROVIDER,
              ITEM_TYPE_COS,
              ITEM_TYPE_OWNER));

  public static final String INSTANCE = "instance";
  public static final String ITEM = "item";
  public static final String RESOURCE_ID = "resourceId";
  public static final String ITEM_TYPE = "itemType";
  public static final String PROPERTY = "property";

  /* SearchTypes */
  public static final String SEARCH_TYPE_GEO = "geoSearch_";
  public static final String SEARCH_TYPE_TEXT = "textSearch_";
  public static final String SEARCH_TYPE_ATTRIBUTE = "attributeSearch_";
  public static final String SEARCH_TYPE_TAGS = "tagsSearch_";
  public static final String SEARCH_TYPE_CRITERIA = "searchCriteria_";
  public static final String SEARCH_CRITERIA_KEY = "searchCriteria";

  /* Response fields */
  public static final String MESSAGE = "detail";
  public static final String RESULTS_KEY = "results";
  public static final String METHOD = "method";
  public static final String HTTP_METHOD = "httpMethod";
  public static final String STATUS = "title";
  public static final String TITLE = "title";
  public static final String DETAIL = "detail";
  public static final String FAILED = "failed";
  public static final String ERROR = "error";
  public static final String DESC = "detail";

  /* DB Query */
  public static final String TOTAL_HITS = "totalHits";
  public static final String INCLUDE_FIELDS = "includeFields";
  public static final String VALUES = "values";
  public static final String DATA_UPLOAD_STATUS = "dataUploadStatus";
  public static final String RESOURCE_SVR_URL = "resourceServer.url";
  public static final String PUBLISH_STATUS = "publishStatus";
  public static final String FILTER_MYASSETS = "filter_myassets";
  public static final String MEDIA_URL = "mediaURL";
  public static final String PENDING = "PENDING";
  public static final String QUERY_KEY = "query";
  public static final String HITS = "hits";
  public static final String TOTAL = "total";
  public static final String TERM = "term";
  public static final String NAME = "name";
  public static final String FILTER = "filter";
  public static final String TAGS = "tags";
  public static final String FILE_FORMAT = "fileFormat";

  /* Temporal Query */
  public static final String BETWEEN_RANGE = "betweenRange";
  public static final String AFTER_RANGE = "afterRange";
  public static final String BEFORE_RANGE = "beforeRange";
  public static final String BETWEEN_TEMPORAL = "betweenTemporal";
  public static final String AFTER_TEMPORAL = "afterTemporal";
  public static final String BEFORE_TEMPORAL = "beforeTemporal";
  public static final String AVERAGE_RATING = "average_rating";
  public static final String TOTAL_RATINGS = "totalRatings";
  public static final String ICON_BASE64 = "icon_base64";
  public static final String PROVIDER_DES = "providerDescription";
  public static final String RESOURCE_COUNT = "resourceCount";
  public static final String PROVIDER_COUNT = "providerCount";
  public static final String RESOURCE_GROUP_COUNT = "resourceGroupCount";

  /* HTTP Methods */
  public static final String REQUEST_GET = "GET";
  public static final String REQUEST_POST = "POST";
  public static final String REQUEST_PUT = "PUT";
  public static final String REQUEST_PATCH = "PATCH";
  public static final String REQUEST_DELETE = "DELETE";

  /* Error Messages */
  public static final String DATABASE_ERROR = "DB Error. Check logs for more information";

  /* Operation type */
  public static final String INSERT = "insert";
  public static final String UPDATE = "update";
  public static final String DELETE = "delete";

  /* Limits/Constraints */
  public static final long COORDINATES_SIZE = 10;
  public static final int COORDINATES_PRECISION = 6;
  public static final int STRING_SIZE = 100;
  public static final int STRING_SIZE_LIMIT = 100;
  public static final int PROPERTY_SIZE = 4;
  public static final int VALUE_SIZE = 4;
  public static final int FILTER_VALUE_SIZE = 10;
  public static final int ID_SIZE = 512;
  public static final int INSTANCE_SIZE = 100;
  public static final int FILTER_PAGINATION_SIZE = 10000;
  public static final int OFFSET_PAGINATION_SIZE = 9999;
  public static final int MAX_RESULT_WINDOW = 10000;
  public static final int MAXDISTANCE_LIMIT = 10000;
  public static final int SERVICE_TIMEOUT = 3000;
  public static final int POPULAR_DATASET_COUNT = 6;
  public static final String FILTER_PAGINATION_FROM = "0";
  public static final String MAX_LIMIT = "10000";
  public static final String SUCCESS = "Success";

  /* Range query keys */
  public static final String GREATER_THAN = "gt";
  public static final String LESS_THAN = "lt";
  public static final String GREATER_THAN_EQUALS = "gte";
  public static final String LESS_THAN_EQUALS = "lte";

  /* Verticle configuration */
  public static final String SERVICE_ADDRESS_KEY = "serviceAddress";

  /* URN Codes - Common */
  public static final String TYPE_SUCCESS = "urn:dx:cat:Success";
  public static final String TYPE_FAIL = "urn:dx:cat:Fail";
  public static final String TYPE_ACCESS_DENIED = "urn:dx:cat:AccessDenied";
  public static final String TYPE_TOKEN_INVALID = "urn:dx:cat:InvalidAuthorizationToken";
  public static final String TYPE_MISSING_TOKEN = "urn:dx:cat:MissingAuthorizationToken";
  public static final String TYPE_ITEM_NOT_FOUND = "urn:dx:cat:ItemNotFound";
  public static final String TYPE_INVALID_SYNTAX = "urn:dx:cat:InvalidSyntax";
  public static final String TYPE_MISSING_PARAMS = "urn:dx:cat:MissingParams";
  public static final String TYPE_INTERNAL_SERVER_ERROR = "urn:dx:cat:InternalError";
  public static final String TYPE_OPERATION_NOT_ALLOWED = "urn:dx:cat:OperationNotAllowed";
  public static final String TYPE_DB_ERROR = "urn:dx:cat:DatabaseError";
  public static final String TYPE_CONFLICT = "urn:dx:cat:Conflicts";
  public static final String TYPE_ID_NONEXISTANT = "urn:dx:cat:IdNonExistant";
  public static final String TYPE_ALREADY_EXISTS = "urn:dx:cat:AlreadyExists";
  public static final String TYPE_WRONG_PROVIDER = "urn:dx:cat:WrongProvider";
  public static final String TYPE_WRONG_RESOURCESERVER = "urn:dx:cat:WrongResourceServer";
  public static final String TYPE_WRONG_RESOURCEGROUP = "urn:dx:cat:WrongResourceGroup";
  public static final String TYPE_INVALID_SCHEMA = "urn:dx:cat:InvalidSchema";

  /* URN Titles - Common */
  public static final String TITLE_SUCCESS = "Success";
  public static final String TITLE_TOKEN_INVALID = "Token is invalid";
  public static final String TITLE_MISSING_TOKEN = "Token is missing";
  public static final String TITLE_ITEM_NOT_FOUND = "Item is not found";
  public static final String TITLE_INVALID_SYNTAX = "Invalid Syntax";
  public static final String TITLE_MISSING_PARAMS = "Missing parameters";
  public static final String TITLE_INTERNAL_SERVER_ERROR = "Internal error";
  public static final String TITLE_OPERATION_NOT_ALLOWED = "Operation not allowed";
  public static final String TITLE_ID_NONEXISTANT = "ID doesn't exist";
  public static final String TITLE_ALREADY_EXISTS = "Item already exists";
  public static final String TITLE_WRONG_PROVIDER = "Wrong Provider";
  public static final String TITLE_WRONG_RESOURCESERVER = "Wrong Resource Server";
  public static final String TITLE_WRONG_RESOURCEGROUP = "Wrong Resource Group";
  public static final String TITLE_INVALID_SCHEMA = "Invalid Schema";

  /* URN Details - Common */
  public static final String DETAIL_CONFLICT = "Conflicts";
  public static final String DETAIL_INTERNAL_SERVER_ERROR = "Internal error";
  public static final String DETAIL_WRONG_ITEM_TYPE = "Wrong Item Type";
  public static final String DETAIL_ID_NOT_FOUND = "id not present in the request";
  public static final String DETAIL_ITEM_NOT_FOUND = "Item not found";
  public static final String DETAIL_INVALID_TOKEN = "Authorization failed, Invalid token.";
  public static final String DETAIL_INVALID_SCHEMA = "Invalid schema provided";
  public static final String NO_CONTENT_AVAILABLE = "No Content Available";

  /* Geo Error URNs */
  public static final String TYPE_INVALID_GEO_PARAM = "urn:dx:cat:InvalidGeoParam";
  public static final String TITLE_INVALID_GEO_PARAM = "Geoquery parameter error";
  public static final String TYPE_INVALID_GEO_VALUE = "urn:dx:cat:InvalidGeoValue";
  public static final String TITLE_INVALID_GEO_VALUE = "Geoquery value error";
  public static final String TITLE_INVALID_UUID = "Invalid syntax of uuid";
  public static final String DETAIL_INVALID_COORDINATE_POLYGON = "Coordinate mismatch (Polygon)";
  public static final String DETAIL_INVALID_BBOX = "Issue with bbox coordinates";
  public static final String DETAIL_INVALID_GEO_PARAMETER = "Missing/Invalid geo parameters";
  public static final String DETAIL_INVALID_RESPONSE_FILTER =
      "Missing/Invalid responseFilter parameters";

  /* Property Error URNs */
  public static final String TYPE_INVALID_PROPERTY_PARAM = "urn:dx:cat:InvalidProperty";
  public static final String TITLE_INVALID_PROPERTY_PARAM = "Invalid Property";
  public static final String TYPE_INVALID_PROPERTY_VALUE = "urn:dx:cat:InvalidPropertyValue";
  public static final String TITLE_INVALID_PROPERTY_VALUE = "Invalid Property Values";
  public static final String TYPE_INVALID_QUERY_PARAM_VALUE = "urn:dx:cat:InvalidParamValue";
  public static final String TYPE_INVALID_UUID = "urn:dx:cat:InvalidUUID";
  public static final String TITLE_INVALID_QUERY_PARAM_VALUE = "Invalid value for a query param";

  /* Text/Bad Query Error URNs */
  public static final String TYPE_BAD_TEXT_QUERY = "urn:dx:cat:BadTextQuery";
  public static final String TITLE_BAD_TEXT_QUERY = "Bad text query values";
  public static final String TYPE_BAD_FILTER = "urn:dx:cat:BadFilter";
  public static final String TITLE_BAD_FILTER = "Bad filters applied";

  /* Misc */
  public static final String INSTANCE_CREATION_SUCCESS = "Instance created successfully.";
  public static final String STAC_CREATION_SUCCESS = "Stac created successfully.";
  public static final String STAC_DELETION_SUCCESS = "Stac deleted successfully.";
  static final String DESCRIPTION = "detail";
  static final String HTTP = "http";
  static final String FILTER_RATING_AGGREGATION = "?filter_path=hits.total.value,aggregations";
  static final String DISTANCE_IN_METERS = "m";
  static final String GEO_RELATION_KEY = "relation";
  static final String EMPTY_RESPONSE = "Empty response";
  static final String COUNT_UNSUPPORTED = "Count is not supported with filtering";
  static final String INVALID_SEARCH = "Invalid search request";
  static final String DOC_EXISTS = "item already exists";

  /* Shared ES Query Templates */
  public static final String TEXT_QUERY = "{\"query_string\":{\"query\":\"$1\"}}";
  public static final String GET_DOC_QUERY =
      "{\"_source\":[$2],\"query\":{\"term\":{\"id.keyword\":\"$1\"}}}";
  public static final String GET_INSTANCE_CASE_INSENSITIVE_QUERY =
      "{\"_source\":[$2], \"query\": {\"match\": {\"id\": \"$1\"}}}";
  public static final String MATCH_QUERY = "{\"match\":{\"$1\":\"$2\"}}";
  public static final String TERM_QUERY = "{\"term\":{\"$1\":\"$2\"}}";
  public static final String BOOL_MUST_QUERY = "{\"query\":{\"bool\":{\"must\":[$1]}}}";
  public static final String BOOL_SHOULD_QUERY = "{\"query\":{\"bool\":{\"should\":[$1]}}}";
  public static final String SHOULD_QUERY = "{\"bool\":{\"should\":$1}}";
  public static final String MUST_QUERY = "{\"bool\":{\"must\":$1}}";
  public static final String FILTER_QUERY = "{\"bool\":{\"filter\":[$1]}}";
  public static final String INSTANCE_FILTER = "{\"match\":{\"instance\": \"$1\"}}";
  public static final String GEO_SHAPE_QUERY =
      "{ \"geo_shape\": { \"$4\": { \"shape\": { \"type\": \"$1\", \"coordinates\": $2 },"
          + " \"relation\": \"$3\" } } }";
}
