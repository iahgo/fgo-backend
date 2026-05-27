package com.example.dto.listagem;

import java.time.LocalDateTime;

public record RemessaItemDto(
        String referencia,
        LocalDateTime dataHoraRecebimento,
        int numeroSequencial,
        String agenteFinanceiro,
        String nomeFundo,
        String situacao,
        String motivoRejeicao,
        Integer quantidadeRegistros,
        Integer quantidadeAceitos,
        Integer quantidadeRecusados
) {
}
