package com.example.service;

import com.example.dto.OperacaoResumoDto;
import com.example.exception.DadosNaoDisponiveisException;
import com.example.repository.OperacaoRedisRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Serviço de domínio para operações de crédito.
 *
 * Responsabilidades:
 *   1. Orquestrar a leitura do Redis via repository
 *   2. Lançar exceções de negócio tipadas (tratadas pelo GlobalExceptionMapper)
 *
 * A validação de agente habilitado será feita via AgenteRepository
 * quando a model Agente estiver criada. Por ora o cod_agente vem
 * autenticado pelo IIB — confiamos que é válido.
 *
 * O que este serviço NÃO faz:
 *   - Não acessa o DB2 (regra inegociável da arquitetura)
 *   - Não serializa/deserializa JSON (responsabilidade do repository)
 *   - Não conhece chaves Redis ou TTL (responsabilidade do repository)
 */
@ApplicationScoped
public class OperacaoService {

    private static final Logger LOG = Logger.getLogger(OperacaoService.class);

    @Inject
    OperacaoRedisRepository redisRepository;

    /**
     * Retorna o resumo de operações de um agente para o mês especificado.
     *
     * @param codAgente código interno do agente (vem do header X-Cod-Agente, injetado pelo IIB)
     * @param mesAno    formato YYYY-MM (ex: "2025-04")
     * @return DTO com KPIs do agente no mês
     * @throws DadosNaoDisponiveisException se não há dados no Redis para este agente/mês
     */
    public OperacaoResumoDto getResumo(int codAgente, String mesAno) {
        return redisRepository.buscar(codAgente, mesAno)
                .orElseThrow(() -> {
                    LOG.warnf("[SERVICE] Cache miss | agente=%d | mesAno=%s", codAgente, mesAno);
                    return new DadosNaoDisponiveisException(codAgente, mesAno);
                });
    }
}
