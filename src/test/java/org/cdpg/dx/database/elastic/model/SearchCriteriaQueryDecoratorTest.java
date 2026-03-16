package org.cdpg.dx.database.elastic.model;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.cdpg.dx.common.exception.DxEsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SearchCriteriaQueryDecorator Tests")
class SearchCriteriaQueryDecoratorTest {

  private Map<FilterType, List<QueryModel>> createEmptyQueryMap() {
    Map<FilterType, List<QueryModel>> map = new EnumMap<>(FilterType.class);
    for (FilterType ft : FilterType.values()) {
      map.put(ft, new ArrayList<>());
    }
    return map;
  }

  @Nested
  @DisplayName("add()")
  class AddTests {

    @Test
    @DisplayName("null request should return queryMap unchanged")
    void nullRequest() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      Map<FilterType, List<QueryModel>> result =
          new SearchCriteriaQueryDecorator(map, null).add();
      assertThat(result.get(FilterType.FILTER)).isEmpty();
    }

    @Test
    @DisplayName("empty searchCriteria should throw DxEsException")
    void emptyCriteria() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      SearchCriteriaRequestDTO dto = new SearchCriteriaRequestDTO(List.of(), null);
      assertThatThrownBy(() -> new SearchCriteriaQueryDecorator(map, dto).add())
          .isInstanceOf(DxEsException.class);
    }

    @Test
    @DisplayName("term search should add FILTER query")
    void termSearch() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      SearchCriteriaDTO criterion = new SearchCriteriaDTO("type.keyword", "term", List.of("iudx:Resource"));
      SearchCriteriaRequestDTO dto = new SearchCriteriaRequestDTO(List.of(criterion), null);
      Map<FilterType, List<QueryModel>> result =
          new SearchCriteriaQueryDecorator(map, dto).add();
      assertThat(result.get(FilterType.FILTER)).hasSize(1);
    }

    @Test
    @DisplayName("betweenRange should add range query with 2 values")
    void betweenRange() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      SearchCriteriaDTO criterion = new SearchCriteriaDTO("price", "betweenRange", List.of(10, 100));
      SearchCriteriaRequestDTO dto = new SearchCriteriaRequestDTO(List.of(criterion), null);
      Map<FilterType, List<QueryModel>> result =
          new SearchCriteriaQueryDecorator(map, dto).add();
      assertThat(result.get(FilterType.FILTER)).hasSize(1);
    }

    @Test
    @DisplayName("betweenRange with wrong number of values should throw")
    void betweenRangeWrongValues() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      SearchCriteriaDTO criterion = new SearchCriteriaDTO("price", "betweenRange", List.of(10));
      SearchCriteriaRequestDTO dto = new SearchCriteriaRequestDTO(List.of(criterion), null);
      assertThatThrownBy(() -> new SearchCriteriaQueryDecorator(map, dto).add())
          .isInstanceOf(DxEsException.class);
    }

    @Test
    @DisplayName("unsupported searchType should throw DxEsException")
    void unsupportedType() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      SearchCriteriaDTO criterion = new SearchCriteriaDTO("field", "unknownType", List.of("val"));
      SearchCriteriaRequestDTO dto = new SearchCriteriaRequestDTO(List.of(criterion), null);
      assertThatThrownBy(() -> new SearchCriteriaQueryDecorator(map, dto).add())
          .isInstanceOf(DxEsException.class);
    }
  }
}
