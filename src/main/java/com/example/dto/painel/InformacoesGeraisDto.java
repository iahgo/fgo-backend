package com.example.dto.painel;

import java.math.BigDecimal;

public record InformacoesGeraisDto(
        long quantidadeMutuarios,
        long quantidadeOperacoes,
        BigDecimal saldoContratadoTotal,
        BigDecimal ticketMedio,
        BigDecimal saldoCarteira
) {
}
