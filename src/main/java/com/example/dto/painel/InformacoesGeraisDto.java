package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de KPIs de Informações Gerais do painel.
 */
public record InformacoesGeraisDto(
        long mutuarios,
        long operacoes,
        BigDecimal saldoContratadoTotal,
        BigDecimal ticketMedio,
        BigDecimal saldoCarteira
) {
}
