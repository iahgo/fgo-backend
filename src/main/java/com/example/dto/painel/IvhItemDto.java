package com.example.dto.painel;

import java.math.BigDecimal;

public record IvhItemDto(
        String codigoPrograma,
        String nomePrograma,
        BigDecimal percentualCobertura,
        BigDecimal valorHonrado,
        BigDecimal valorRecuperado,
        BigDecimal valorContratado,
        BigDecimal ivh
) {
}
