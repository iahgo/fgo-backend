package com.example.dto.painel;

import java.util.List;

/**
 * DTO de resumo de pendências do painel.
 */
public record PendenciasResumoDto(List<PendenciasGrupoDto> grupos) {
}
