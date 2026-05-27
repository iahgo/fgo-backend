package com.example.dto.listagem;

import java.time.LocalDate;

public record PendenciaItemDto(
        String nomeFundo,
        String nomePrograma,
        String numeroContrato,
        String situacaoContrato,
        String tipoPendencia,
        LocalDate dataInicioPendencia
) {
}
