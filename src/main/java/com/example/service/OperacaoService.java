package com.example.service;

import com.example.dto.OperacaoResumoDto;
import com.example.exception.DadosNaoDisponiveisException;
import com.example.repository.OperacaoRedisRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OperacaoService {

    private static final Logger LOG = Logger.getLogger(OperacaoService.class);

    @Inject OperacaoRedisRepository redisRepository;

    public OperacaoResumoDto getResumo(int codAgente) {
        return redisRepository.buscar(codAgente)
                .orElseThrow(() -> {
                    LOG.warnf("[SERVICE] Cache miss | agente=%d", codAgente);
                    return new DadosNaoDisponiveisException(codAgente, "atual");
                });
    }
}
