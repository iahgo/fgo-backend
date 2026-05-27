package com.example.dto.listagem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimentacaoItemDto(
        String nomeFundo,
        int numeroSequencialRemessa,
        String tipoMovimentacaoFinanceira,
        String situacaoMovFinanceira,
        String situacaoRemessa,
        LocalDate dataProcessamento,
        LocalDate dataAtualizacaoMonetaria,
        LocalDate dataMovimentacaoFinanceira,
        Integer quantidadeRegistros,
        BigDecimal valorLiquidoMovimentado
) {
}
