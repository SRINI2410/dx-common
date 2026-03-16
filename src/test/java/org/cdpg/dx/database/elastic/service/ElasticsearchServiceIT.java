package org.cdpg.dx.database.elastic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.testutil.ElasticsearchTestBase;
import org.junit.jupiter.api.*;

/**
 * Integration tests for {@link ElasticsearchService} against a real Elasticsearch container via
 * Testcontainers.
 *
 * <p>These tests exercise the full service proxy serialization chain:
 *
 * <pre>
 *   Test → EBProxy.toJson() → EventBus → ProxyHandler.fromJson() → ElasticsearchServiceImpl → ES
 * </pre>
 *
 * <p>Key serialization concern: {@link QueryModel} uses {@code Map<String, Object>} for
 * queryParameters. Documents must be set via {@code createQueryModelFromDocument(JsonObject)} which
 * correctly populates the nested {@code queries} field that {@code extractDocumentFromQueryModel()}
 * reads from.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ElasticsearchServiceIT extends ElasticsearchTestBase {

  private static final String TEST_INDEX =
      "test-index-" + UUID.randomUUID().toString().substring(0, 8);
  private String insertedDocId;

  @Test
  @Order(1)
  @DisplayName("createIndex - should create a new index")
  void testCreateIndex(VertxTestContext ctx) {
    JsonObject mappings =
        new JsonObject()
            .put(
                "mappings",
                new JsonObject()
                    .put(
                        "properties",
                        new JsonObject()
                            .put("name", new JsonObject().put("type", "text"))
                            .put("value", new JsonObject().put("type", "integer"))));

    assertFutureSuccess(
        elasticsearchService.createIndex(TEST_INDEX, mappings),
        ctx,
        result -> {
          // createIndex returns Future<Void>, success means no exception
        });
  }

  @Test
  @Order(2)
  @DisplayName(
      "createDocuments - should insert documents via createQueryModelFromDocument and return IDs")
  void testCreateDocuments(VertxTestContext ctx) {
    // Use createQueryModelFromDocument() which correctly sets the nested queries structure
    // that extractDocumentFromQueryModel() reads from in ElasticsearchServiceImpl
    QueryModel doc = new QueryModel();
    JsonObject document = new JsonObject().put("name", "test document").put("value", 42);
    doc.createQueryModelFromDocument(document);

    elasticsearchService
        .createDocuments(TEST_INDEX, List.of(doc))
        .onComplete(
            ctx.succeeding(
                ids ->
                    ctx.verify(
                        () -> {
                          assertThat(ids).isNotNull().isNotEmpty();
                          insertedDocId = ids.get(0);
                          assertThat(insertedDocId).isNotBlank();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(3)
  @DisplayName("count - should return document count >= 1 after indexing")
  void testCount(VertxTestContext ctx) throws InterruptedException {
    // ES needs a short delay for the document to be indexed and searchable
    Thread.sleep(1500);

    QueryModel matchAll = new QueryModel(QueryType.MATCH_ALL);

    assertFutureSuccess(
        elasticsearchService.count(TEST_INDEX, matchAll),
        ctx,
        count -> assertThat(count).isGreaterThanOrEqualTo(1));
  }

  @Test
  @Order(4)
  @DisplayName("deleteDocument - should delete a document by ID")
  void testDeleteDocument(VertxTestContext ctx) {
    assertFutureSuccess(
        elasticsearchService.deleteDocument(TEST_INDEX, insertedDocId),
        ctx,
        result -> {
          // deleteDocument returns Future<Void>, success means no exception
        });
  }
}
