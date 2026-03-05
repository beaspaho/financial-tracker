package com.test.financialtracker.common.wrapper;

import java.util.List;

/**
 * Generic paginated response wrapper for admin list endpoints.

 * @param <T> the item type in the page
 */
public record PagedResponse<T>(
        List<T> items,
        int     page,
        int     pageSize,
        long    totalItems,
        int     totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PagedResponse<T> of(List<T> items, int page, int pageSize, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        return new PagedResponse<>(
                items,
                page,
                pageSize,
                totalItems,
                totalPages,
                page < totalPages - 1,
                page > 0
        );
    }
}