package org.cdpg.dx.common.exception;

/**
 * Centralized error codes shared across DX microservices.
 *
 * <p>Range allocations:
 * <ul>
 *   <li>10000–10999: General / validation errors</li>
 *   <li>11000–11999: PostgreSQL / database errors</li>
 *   <li>12000–12999: Authentication / authorization errors</li>
 *   <li>22000–22999: Subscription errors</li>
 *   <li>33000–33999: Search errors</li>
 *   <li>44000–44999: RabbitMQ / messaging errors</li>
 *   <li>60000–60999: Timeout errors</li>
 *   <li>80000–80999: CSV / streaming errors</li>
 * </ul>
 */
public class DxErrorCodes {

  // 10000 – 10999: General / Validation
  public static final int DEFAULT_CODE = 10000;
  public static final int VALIDATION_ERROR = 10001;
  public static final int NOT_FOUND = 10002;
  public static final int INTERNAL_ERROR = 10004;
  public static final int AUTH_ERROR = 10005;
  public static final int BAD_REQUEST = 10006;
  public static final int NOT_ACCEPTABLE = 10007;

  // 11000 – 11999: PostgreSQL / DB
  public static final int PG_ERROR = 11000;
  public static final int PG_NO_ROW_ERROR = 11001;
  public static final int PG_INVALID_COL_ERROR = 11002;
  public static final int PG_UNIQUE_CONSTRAINT_VIOLATION_ERROR = 11003;

  // 12000 – 12999: Auth
  public static final int UNAUTHORIZED = 12000;
  public static final int FORBIDDEN = 12001;
  public static final int TOKEN_INVALID = 12002;
  public static final int KEYCLOAK_SERVICE_ERROR = 12100;
  public static final int FORBIDDEN_NO_ACCESS = 12011;
  public static final int FORBIDDEN_ACCESS_PENDING = 12012;
  public static final int FORBIDDEN_ACCESS_REJECTED = 12013;
  public static final int TOO_MANY_REQUESTS = 12014;
  public static final int ES_ERROR = 12000;

  // 2000: Runtime
  public static final int RUNTIME_ERROR = 2000;

  // 22000 – 22999: Subscriptions
  public static final int SUBS_ERROR = 22000;
  public static final int SUBS_QUEUE_EXISTS = 22001;
  public static final int SUBS_QUEUE_REGISTRATION_FAILED = 22002;
  public static final int SUBS_QUEUE_BINDING_FAILED = 22003;
  public static final int SUBS_QUEUE_NOT_FOUND = 22004;
  public static final int SUBS_QUEUE_DELETION_FAILED = 22005;
  public static final int SUBS_EXCHANGE_NOT_FOUND = 22006;

  // 33000 – 33999: Search
  public static final int SEARCH_ERROR = 33000;

  // 44000 – 44999: RabbitMQ / Messaging
  public static final int RABBIT_MQ_ERROR = 44000;
  public static final int RABBIT_MQ_QUEUE_EXISTS = 44001;
  public static final int RABBIT_MQ_QUEUE_REGISTRATION_FAILED = 44002;
  public static final int RABBIT_MQ_QUEUE_BINDING_FAILED = 44003;

  // Redis (dataplane-specific codes reused from 5000 range)
  public static final int REDIS_ERROR = 44000;
  public static final int KEY_NOT_FOUND = 5001;
  public static final int CONNECTION_ERROR = 5002;
  public static final int INVALID_JSON_PATH = 5003;
  public static final int OPERATION_FAILED = 5004;
  public static final int INVALID_RESPONSE = 5005;
  public static final int REDIS_TIMEOUT = 5006;

  // 60000: Timeout
  public static final int TIMEOUT_ERROR = 60000;

  // 80000: CSV / Streaming
  public static final int CONFLICT = 409;
  public static final int CSV_STREAM_ERROR = 80000;
}
