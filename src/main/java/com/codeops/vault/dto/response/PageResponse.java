package com.codeops.vault.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Matches the pagination pattern used across all CodeOps services.
 *
 * @param content       The elements in the current page.
 * @param page          Current page number (zero-based).
 * @param size          Page size.
 * @param totalElements Total number of elements across all pages.
 * @param totalPages    Total number of pages.
 * @param isLast        Whether this is the last page.
 * @param <T>           The type of elements in the page.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isLast
) {
    /**
     * Creates a PageResponse from a Spring Data Page.
     *
     * @param page The Spring Data Page to convert.
     * @param <T>  The type of elements in the page.
     * @return A new PageResponse containing the page data.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
