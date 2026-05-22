package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de um período da série histórica do IVH.
 */
public record PeriodoIvhDto(String periodo, BigDecimal ivh) {
}
