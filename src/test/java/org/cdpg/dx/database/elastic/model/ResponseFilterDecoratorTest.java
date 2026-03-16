package org.cdpg.dx.database.elastic.model;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.cdpg.dx.common.exception.DxEsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResponseFilterDecorator Tests")
class ResponseFilterDecoratorTest {

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
          new ResponseFilterDecorator(map, null).add();
      assertThat(result.get(FilterType.INCLUDES)).isEmpty();
    }

    @Test
    @DisplayName("non-matching searchType should return queryMap unchanged")
    void nonMatchingSearchType() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      ResponseFilterRequestDTO dto = new ResponseFilterRequestDTO("textSearch", false, List.of("name"), null);
      Map<FilterType, List<QueryModel>> result =
          new ResponseFilterDecorator(map, dto).add();
      assertThat(result.get(FilterType.INCLUDES)).isEmpty();
    }

    @Test
    @DisplayName("valid response filter should add includes")
    void validFilter() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      ResponseFilterRequestDTO dto = new ResponseFilterRequestDTO("responseFilter", false, List.of("id", "name"), null);
      Map<FilterType, List<QueryModel>> result =
          new ResponseFilterDecorator(map, dto).add();
      assertThat(result.get(FilterType.INCLUDES)).hasSize(1);
    }

    @Test
    @DisplayName("missing attribute and filter should throw DxEsException")
    void missingAttribute() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      ResponseFilterRequestDTO dto = new ResponseFilterRequestDTO("responseFilter", false, null, null);
      assertThatThrownBy(() -> new ResponseFilterDecorator(map, dto).add())
          .isInstanceOf(DxEsException.class);
    }
  }
}
