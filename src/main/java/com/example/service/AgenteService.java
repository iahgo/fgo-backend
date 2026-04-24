package com.example.service;

import com.example.dto.AgenteDto;
import com.example.repository.OperacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço para listagem de agentes financeiros cadastrados.
 * Lê diretamente do DB2 — tabela pequena, sem cache Redis.
 */
@ApplicationScoped
public class AgenteService {

    @Inject
    OperacaoRepository repository;

    /**
     * Retorna todos os agentes financeiros como DTOs, incluindo o total de operações
     * de cada agente em OPR_CRD_FNDO_GRTR.
     * Agentes sem operações aparecem com totalOperacoes=0 — isso explica por que
     * 40 agentes estão cadastrados mas apenas 7 têm dados no Redis (só esses 7
     * possuem operações na tabela).
     */
    public List<AgenteDto> listarTodos() {
        // Mapa codAgente → totalOperacoes (vem apenas dos agentes com operações)
        Map<Integer, Long> contagens = new HashMap<>();
        for (Object[] row : repository.contarOperacoesPorAgente()) {
            contagens.put((Integer) row[0], (Long) row[1]);
        }

        return repository.buscarTodosAgentes().stream()
                .map(a -> new AgenteDto(
                        a.getCdAgtFnco(),
                        a.getNmAbvdAgtFnco().trim(),
                        contagens.getOrDefault(a.getCdAgtFnco(), 0L)))
                .toList();
    }
}
