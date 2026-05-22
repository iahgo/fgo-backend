package com.example.dto.listagem;

import java.math.BigDecimal;

/**
 * DTO de detalhe de movimentação (breakdown por conta de movimento de garantia).
 */
public record MovimentacaoDetalheDto(
        String tipoMovimentacao,
        BigDecimal valorNominal,
        BigDecimal atualizacaoMonetaria,
        BigDecimal valorLiquido
) {
}
