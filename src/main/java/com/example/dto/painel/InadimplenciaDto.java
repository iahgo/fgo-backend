package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de inadimplência do painel.
 */
public record InadimplenciaDto(
        BigDecimal saldoAtrasado,
        BigDecimal indiceInadimplenciaSaldo,
        BigDecimal indiceInadimplenciaOperacoes
) {
}
