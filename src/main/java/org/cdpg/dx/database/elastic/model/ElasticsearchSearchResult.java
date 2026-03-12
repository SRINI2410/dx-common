package org.cdpg.dx.database.elastic.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable result container for Elasticsearch search operations.
 *
 * <p>Bundles the search hits, total hit count, and aggregation results into a single value object.
 * This replaces the thread-unsafe static fields that were previously used in {@link
 * ElasticsearchResponse} to carry {@code totalHits} and {@code aggregations}.
 *
 * <p>Annotated as {@code @DataObject} for Vert.x {@code @ProxyGen} compatibility, enabling
 * transparent serialization over the EventBus.
 */
@DataObject(generateConverter = true)
public class ElasticsearchSearchResult {

  private List<ElasticsearchResponse> results;
  private int totalHits;
  private JsonObject aggregations;

  /** Default constructor. */
  public ElasticsearchSearchResult() {
    this.results = new ArrayList<>();
    this.totalHits = 0;
    this.aggregations = new JsonObject();
  }

  /**
   * Primary constructor.
   *
   * @param results list of search hits
   * @param totalHits the total number of matching documents (not just this page)
   * @param aggregations aggregation results (empty JsonObject if none)
   */
  public ElasticsearchSearchResult(
      List<ElasticsearchResponse> results, int totalHits, JsonObject aggregations) {
    this.results = results != null ? results : new ArrayList<>();
    this.totalHits = totalHits;
    this.aggregations = aggregations != null ? aggregations : new JsonObject();
  }

  /**
   * Constructor from JsonObject — required by Vert.x {@code @DataObject} / {@code @ProxyGen}.
   *
   * @param json the serialized form
   */
  public ElasticsearchSearchResult(JsonObject json) {
    JsonArray arr = json.getJsonArray("results", new JsonArray());
    this.results = new ArrayList<>();
    for (int i = 0; i < arr.size(); i++) {
      this.results.add(new ElasticsearchResponse(arr.getJsonObject(i)));
    }
    this.totalHits = json.getInteger("totalHits", 0);
    this.aggregations = json.getJsonObject("aggregations", new JsonObject());
  }

  /**
   * Serialize to JsonObject — required by Vert.x {@code @DataObject} / {@code @ProxyGen}.
   *
   * @return the serialized form
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JsonArray arr = new JsonArray();
    if (results != null) {
      for (ElasticsearchResponse res : results) {
        arr.add(res.toJson());
      }
    }
    json.put("results", arr);
    json.put("totalHits", totalHits);
    json.put("aggregations", aggregations);
    return json;
  }

  public List<ElasticsearchResponse> getResults() {
    return results;
  }

  public void setResults(List<ElasticsearchResponse> results) {
    this.results = results;
  }

  public int getTotalHits() {
    return totalHits;
  }

  public void setTotalHits(int totalHits) {
    this.totalHits = totalHits;
  }

  public JsonObject getAggregations() {
    return aggregations;
  }

  public void setAggregations(JsonObject aggregations) {
    this.aggregations = aggregations;
  }

  @Override
  public String toString() {
    return "ElasticsearchSearchResult{"
        + "totalHits="
        + totalHits
        + ", resultsCount="
        + (results != null ? results.size() : 0)
        + ", hasAggregations="
        + (aggregations != null && !aggregations.isEmpty())
        + '}';
  }
}
