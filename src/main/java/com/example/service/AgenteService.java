package com.example.service;

import com.example.dto.AgenteDto;
import com.example.repository.OperacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Serviço para listagem de agentes financeiros cadastrados.
 * Lê diretamente do DB2 — tabela pequena, sem cache Redis.
 */
@ApplicationScoped
public class AgenteService {

    @Inject
    OperacaoRepository repository;

    /**
     * Retorna todos os agentes financeiros como DTOs.
     * O nome vem do DB2 com espaços de padding (CHAR(60)) — faz trim.
     */
    public List<AgenteDto> listarTodos() {
        return repository.buscarTodosAgentes().stream()
                .map(a -> new AgenteDto(a.getCdAgtFnco(), a.getNmAbvdAgtFnco().trim()))
                .toList();
    }
}
