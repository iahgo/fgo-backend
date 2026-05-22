package com.example.dto.painel;

import java.math.BigDecimal;

/**
 * DTO de grupo de pendências do resumo.
 */
public record PendenciasGrupoDto(String tipo, String label, BigDecimal valor) {
}
