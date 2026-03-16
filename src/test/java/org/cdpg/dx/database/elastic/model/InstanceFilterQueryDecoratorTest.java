package org.cdpg.dx.database.elastic.model;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InstanceFilterQueryDecorator Tests")
class InstanceFilterQueryDecoratorTest {

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
          new InstanceFilterQueryDecorator(map, null).add();
      assertThat(result.get(FilterType.FILTER)).isEmpty();
    }

    @Test
    @DisplayName("empty instance should return queryMap unchanged")
    void emptyInstance() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      Map<FilterType, List<QueryModel>> result =
          new InstanceFilterQueryDecorator(map, new InstanceFilterRequestDTO("")).add();
      assertThat(result.get(FilterType.FILTER)).isEmpty();
    }

    @Test
    @DisplayName("valid instance should add term filter")
    void validInstance() {
      Map<FilterType, List<QueryModel>> map = createEmptyQueryMap();
      Map<FilterType, List<QueryModel>> result =
          new InstanceFilterQueryDecorator(map, new InstanceFilterRequestDTO("pune.iudx.org.in")).add();
      assertThat(result.get(FilterType.FILTER)).hasSize(1);
    }
  }
}
