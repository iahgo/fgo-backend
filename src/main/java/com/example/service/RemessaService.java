package com.example.service;

import com.example.domain.RemessaAgente;
import com.example.dto.RemessaDto;
import com.example.repository.RemessaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Serviço para consulta de remessas do agente financeiro.
 * Lê diretamente do DB2 — dados transacionais, sem cache.
 */
@ApplicationScoped
public class RemessaService {

    // Labels para CD_TIP_EST_RMS (estado da remessa)
    private static final String[] STATUS_LABELS = {
        "", "Recebida", "Em processamento", "Processada", "Rejeitada", "Cancelada"
    };

    @Inject
    RemessaRepository repository;

    /**
     * Lista as remessas mais recentes de um agente (padrão: últimas 50).
     */
    public List<RemessaDto> listarPorAgente(int codAgente) {
        return listarPorAgente(codAgente, 50);
    }

    /**
     * Lista as remessas mais recentes de um agente com limite configurável.
     */
    public List<RemessaDto> listarPorAgente(int codAgente, int limite) {
        return repository.buscarPorAgente(codAgente, limite).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Busca uma remessa específica validando que pertence ao agente.
     * Retorna empty se não encontrada ou se o agente não tiver acesso.
     */
    public Optional<RemessaDto> buscarPorId(int idRemessa, int codAgente) {
        return repository.buscarPorIdEAgente(idRemessa, codAgente)
                .map(this::toDto);
    }

    private RemessaDto toDto(RemessaAgente r) {
        short est = r.getCdTipEstRms();
        String status = (est >= 1 && est < STATUS_LABELS.length)
                ? STATUS_LABELS[est]
                : "Desconhecido (" + est + ")";

        return new RemessaDto(
                r.getCdRmsAgtFnco(),
                r.getCdAgtFnco(),
                r.getCdFndoGrtr(),
                r.getNrSeqlRms(),
                r.getNmDtst() != null ? r.getNmDtst().trim() : null,
                r.getTsRctRms(),
                r.getDtPrctEvt(),
                r.getDtAtlMntr(),
                status,
                r.getQtRegRms(),
                r.getVlLodoMvtcRms()
        );
    }
}
