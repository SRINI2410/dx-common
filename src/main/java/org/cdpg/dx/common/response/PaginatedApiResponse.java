package org.cdpg.dx.common.response;

import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

/**
 * Generic paginated API response wrapper.
 *
 * <p>Used by controllers to wrap a list of results with pagination metadata
 * before sending via {@link ResponseBuilder}.
 *
 * @param <T> the type of items in the result list
 */
public record PaginatedApiResponse<T>(List<T> result, PaginationInfo paginationInfo) {}
