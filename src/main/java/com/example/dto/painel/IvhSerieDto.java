package com.example.dto.painel;

import java.util.List;

/**
 * DTO de série histórica do IVH.
 */
public record IvhSerieDto(List<PeriodoIvhDto> series) {
}
