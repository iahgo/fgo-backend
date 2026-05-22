package com.example.dto.painel;

import java.util.List;

/**
 * DTO de série histórica de movimentação financeira.
 */
public record MovimentacaoSerieDto(List<PeriodoMovimentacaoDto> series) {
}
