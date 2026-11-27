package com.company.crm.common.pagination;

import com.company.crm.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds safe Pageables from request params: size defaults and is capped by config, and only
 * whitelisted sort fields are accepted (a client can never sort on an arbitrary property).
 *
 * <p>Paging is opt-in: list endpoints keep their original unpaged array response when neither
 * page nor size is sent, so existing screens are unaffected.
 */
@Component
public class PageRequestFactory {

    @Value("${app.pagination.default-size:20}")
    private int defaultSize;

    @Value("${app.pagination.max-size:100}")
    private int maxSize;

    /** True when the caller asked for a page (and therefore gets a PageResponse). */
    public static boolean isPaged(Integer page, Integer size) {
        return page != null || size != null;
    }

    /**
     * @param sort         "field" or "field,asc|desc"; null → {@code defaultSort}
     * @param sortableBy   API sort name → entity property path
     */
    public Pageable of(Integer page, Integer size, String sort, Map<String, String> sortableBy, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw ApiException.badRequest("page must be 0 or greater");
        }
        int pageSize = size == null ? defaultSize : size;
        if (pageSize < 1) {
            throw ApiException.badRequest("size must be at least 1");
        }
        return PageRequest.of(pageNumber, Math.min(pageSize, maxSize), parseSort(sort, sortableBy, defaultSort));
    }

    private Sort parseSort(String sort, Map<String, String> sortableBy, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String property = sortableBy.get(parts[0].trim());
        if (property == null) {
            throw ApiException.badRequest("Cannot sort by '" + parts[0].trim() + "'. Allowed: " + sortableBy.keySet());
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        // Tie-break on id so pages are stable when many rows share the sort value.
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
