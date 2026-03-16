package org.cdpg.dx.common.request;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PaginationRequestBuilder Tests")
class PaginationRequestBuilderTest {

  private RoutingContext ctx;
  private HttpServerRequest request;
  private MultiMap queryParams;
  private MultiMap requestParams;

  @BeforeEach
  void setUp() {
    ctx = mock(RoutingContext.class);
    request = mock(HttpServerRequest.class);
    queryParams = MultiMap.caseInsensitiveMultiMap();
    requestParams = MultiMap.caseInsensitiveMultiMap();
    when(ctx.request()).thenReturn(request);
    when(ctx.queryParams()).thenReturn(queryParams);
    when(request.params(true)).thenReturn(requestParams);
  }

  @Nested
  @DisplayName("Default pagination")
  class DefaultPaginationTests {

    @Test
    @DisplayName("should use default page=1 and size=10 when not specified")
    void defaultPageAndSize() {
      PaginatedRequest result = PaginationRequestBuilder.from(ctx).build();
      assertThat(result.page()).isEqualTo(1);
      assertThat(result.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("should parse custom page and size")
    void customPageAndSize() {
      requestParams.add("page", "3");
      requestParams.add("size", "25");
      queryParams.add("page", "3");
      queryParams.add("size", "25");
      when(ctx.queryParam("page")).thenReturn(List.of("3"));
      when(ctx.queryParam("size")).thenReturn(List.of("25"));

      PaginatedRequest result = PaginationRequestBuilder.from(ctx).build();
      assertThat(result.page()).isEqualTo(3);
      assertThat(result.size()).isEqualTo(25);
    }
  }

  @Nested
  @DisplayName("Filter mapping")
  class FilterMappingTests {

    @Test
    @DisplayName("should map API param names to DB column names")
    void mapsFilters() {
      requestParams.add("status", "active");
      queryParams.add("status", "active");

      PaginatedRequest result = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(Map.of("status", "status_column"))
          .build();

      assertThat(result.filters()).containsKey("status_column");
    }

    @Test
    @DisplayName("should reject unknown query parameters")
    void rejectsUnknown() {
      requestParams.add("unknownParam", "value");

      assertThatThrownBy(() -> PaginationRequestBuilder.from(ctx).build())
          .isInstanceOf(DxBadRequestException.class)
          .hasMessageContaining("Invalid query parameter");
    }
  }

  @Nested
  @DisplayName("Sort")
  class SortTests {

    @Test
    @DisplayName("should parse sort parameter")
    void parsesSort() {
      requestParams.add("sort", "name:asc");
      queryParams.add("sort", "name:asc");

      PaginatedRequest result = PaginationRequestBuilder.from(ctx)
          .allowedSortFields(Set.of("name"))
          .apiToDbMap(Map.of("name", "name_column"))
          .build();

      assertThat(result.orderByList()).hasSize(1);
      assertThat(result.orderByList().get(0).getColumn()).isEqualTo("name_column");
    }

    @Test
    @DisplayName("should reject invalid sort field")
    void rejectsInvalidField() {
      requestParams.add("sort", "unknown:asc");
      queryParams.add("sort", "unknown:asc");

      assertThatThrownBy(() -> PaginationRequestBuilder.from(ctx)
          .allowedSortFields(Set.of("name"))
          .apiToDbMap(Map.of("name", "name_column"))
          .build())
          .isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("should use default sort when no sort param")
    void usesDefaultSort() {
      PaginatedRequest result = PaginationRequestBuilder.from(ctx)
          .defaultSort("created_at", "desc")
          .build();

      assertThat(result.orderByList()).hasSize(1);
      assertThat(result.orderByList().get(0).getColumn()).isEqualTo("created_at");
    }

    @Test
    @DisplayName("should reject more than MAX_SORT_FIELDS")
    void rejectsTooManyFields() {
      requestParams.add("sort", "a:asc;b:asc;c:asc;d:asc");
      queryParams.add("sort", "a:asc;b:asc;c:asc;d:asc");

      assertThatThrownBy(() -> PaginationRequestBuilder.from(ctx)
          .allowedSortFields(Set.of("a", "b", "c", "d"))
          .apiToDbMap(Map.of("a", "a", "b", "b", "c", "c", "d", "d"))
          .build())
          .isInstanceOf(DxBadRequestException.class)
          .hasMessageContaining("Too many sort fields");
    }
  }
}
