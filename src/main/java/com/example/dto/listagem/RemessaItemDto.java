package com.example.dto.listagem;

import java.time.LocalDateTime;

/**
 * DTO de item da listagem paginada de remessas.
 */
public record RemessaItemDto(
        String referencia,
        LocalDateTime dataHoraRecebimento,
        int nrSequencial,
        String agenteFinanceiro,
        String nmFundo,
        String situacao,
        String motivoRejeicao,
        Integer qtdeRegistros,
        Integer registrosAceitos,
        Integer registrosRecusados
) {
}
