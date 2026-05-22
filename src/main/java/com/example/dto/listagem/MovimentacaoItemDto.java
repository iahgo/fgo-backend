package com.example.dto.listagem;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de item da listagem paginada de movimentações.
 */
public record MovimentacaoItemDto(
        String nmFundo,
        int nrSequencialRemessa,
        String tipoMovFinanceira,
        String situacaoMovFinanceira,
        String situacaoRemessa,
        LocalDate dataProcRemessa,
        LocalDate dataAtualMonetaria,
        LocalDate dataMovFinanceira,
        Integer qtdeRegistrosRemessa,
        BigDecimal valorLiqMovimentado
) {
}
