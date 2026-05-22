package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de item da tabela IVH (Índice de Valor Honrado) por programa.
 */
public record IvhItemDto(
        String cdPrograma,
        String nmPrograma,
        BigDecimal cobertura,
        BigDecimal vlHonrados,
        BigDecimal vlRecuperados,
        BigDecimal vlContratado,
        BigDecimal ivh
) {
}
