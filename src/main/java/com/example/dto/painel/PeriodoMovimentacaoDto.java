package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de um período da série histórica de movimentação financeira.
 */
public record PeriodoMovimentacaoDto(
        String periodo,
        BigDecimal carteiraAgente,
        BigDecimal carteiraFundo
) {
}
