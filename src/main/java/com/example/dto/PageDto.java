package com.example.dto;

import java.util.List;

/**
 * DTO genérico de paginação.
 *
 * @param <T> tipo do item da página
 */
public record PageDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageDto<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageDto<>(content, page, size, totalElements, totalPages);
    }
}
