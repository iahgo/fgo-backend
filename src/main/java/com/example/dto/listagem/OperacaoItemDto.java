package com.example.dto.listagem;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de item da listagem paginada de operações.
 */
public record OperacaoItemDto(
        String nmFundo,
        String nmPrograma,
        String nrOperacao,
        String publicoAlvo,
        String estadoOperacao,
        LocalDate dataFormal,
        LocalDate dataVencimento,
        BigDecimal valorOperacao,
        BigDecimal valorLiberado
) {
}
