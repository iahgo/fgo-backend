package com.example.dto.listagem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OperacaoItemDto(
        String nomeFundo,
        String nomePrograma,
        String numeroOperacao,
        String publicoAlvo,
        String estadoOperacao,
        LocalDate dataFormalizacao,
        LocalDate dataVencimento,
        BigDecimal valorOperacao,
        BigDecimal valorLiberado
) {
}
