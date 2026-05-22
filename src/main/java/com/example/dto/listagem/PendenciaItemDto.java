package com.example.dto.listagem;

import java.time.LocalDate;

/**
 * DTO de item da listagem paginada de pendências.
 */
public record PendenciaItemDto(
        String nmFundo,
        String nmPrograma,
        String nrContrato,
        String situacaoContrato,
        String tipoPendencia,
        LocalDate dataInicioPendencia
) {
}
