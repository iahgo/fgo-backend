package com.example.repository;

import com.example.config.FgoConfig;
import com.example.dto.OperacaoResumoDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Repositório de acesso ao Redis para o domínio de Operações.
 *
 * Responsabilidade única: encapsular TODAS as operações Redis deste domínio.
 * Nenhuma outra classe acessa o RedisDataSource diretamente.
 *
 * Operações:
 *   - salvar(dto)              → SET + EXPIRE (com TTL de 25h)
 *   - buscar(codAgente, mes)   → GET + deserializar JSON
 *   - existe(codAgente, mes)   → EXISTS (verificação rápida)
 *   - adquirirLock(codAgente)  → SET NX + EX (lock distribuído)
 *   - liberarLock(codAgente)   → DEL (liberação do lock)
 *
 * Convenção de chaves (cf. arquitetura FGO):
 *   Dados:  af:{codAgente}:operacao:resumo:{mesAno}
 *   Lock:   lock:operacao:{codAgente}
 */
@ApplicationScoped
public class OperacaoRedisRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRedisRepository.class);

    private static final String PADRAO_CHAVE_DADOS = "af:%d:operacao:resumo:%s";
    private static final String PADRAO_CHAVE_LOCK  = "lock:operacao:%d";

    @Inject
    RedisDataSource redis;

    @Inject
    FgoConfig config;

    @Inject
    ObjectMapper objectMapper;

    // =========================================================================
    // OPERAÇÕES DE DADOS
    // =========================================================================

    /**
     * Persiste o resumo de operações de um agente no Redis.
     * Aplica TTL de 25h (configurável em fgo.cache.ttl-dados).
     */
    public void salvar(OperacaoResumoDto dto) {
        String chave = chaveResumo(dto.getCodAgente(), dto.getMesAno());
        try {
            String json = objectMapper.writeValueAsString(dto);
            ValueCommands<String, String> values = redis.value(String.class);
            values.set(chave, json);
            redis.key(String.class).expire(chave, config.cache().ttlDados());
            LOG.debugf("[REPOSITORY-REDIS] Salvo | chave=%s | TTL=%s", chave, config.cache().ttlDados());
        } catch (Exception e) {
            throw new RedisRepositoryException("Falha ao salvar no Redis | chave=" + chave, e);
        }
    }

    /**
     * Busca o resumo de operações de um agente no Redis.
     * Retorna Optional.empty() se a chave não existir (cache miss).
     */
    public Optional<OperacaoResumoDto> buscar(int codAgente, String mesAno) {
        String chave = chaveResumo(codAgente, mesAno);
        try {
            String json = redis.value(String.class).get(chave);
            if (json == null) {
                LOG.debugf("[REPOSITORY-REDIS] Miss | chave=%s", chave);
                return Optional.empty();
            }
            LOG.debugf("[REPOSITORY-REDIS] Hit  | chave=%s", chave);
            return Optional.of(objectMapper.readValue(json, OperacaoResumoDto.class));
        } catch (Exception e) {
            throw new RedisRepositoryException("Falha ao buscar no Redis | chave=" + chave, e);
        }
    }

    /**
     * Verifica se existe um resumo para o agente/mês sem carregar o JSON.
     * Operação O(1) no Redis — usada pelo StartupEvent e CacheGuardian.
     */
    public boolean existe(int codAgente, String mesAno) {
        String chave = chaveResumo(codAgente, mesAno);
        return redis.key(String.class).exists(chave);
    }

    // =========================================================================
    // LOCK DISTRIBUÍDO (SET NX)
    // =========================================================================

    /**
     * Tenta adquirir o lock de carregamento para um agente específico.
     *
     * Usa SET NX (SET if Not eXists) para garantir exclusão mútua entre pods:
     *   - Retorna true  → lock adquirido, este pod é responsável pelo carregamento
     *   - Retorna false → outro pod já tem o lock, pule este agente
     *
     * TTL do lock: 5 minutos (auto-liberação em caso de crash).
     */
    public boolean adquirirLock(int codAgente) {
        String chave = chaveLock(codAgente);
        String resultado = redis.value(String.class)
                .set(chave, "locked", new SetArgs().nx().ex(config.cache().ttlLock()));
        boolean adquirido = "OK".equals(resultado);
        if (adquirido) {
            LOG.debugf("[REPOSITORY-REDIS] Lock adquirido | agente=%d", codAgente);
        }
        return adquirido;
    }

    /**
     * Libera o lock de carregamento de um agente.
     * Sempre chamado no finally do loader para evitar lock permanente.
     */
    public void liberarLock(int codAgente) {
        redis.key(String.class).del(chaveLock(codAgente));
        LOG.debugf("[REPOSITORY-REDIS] Lock liberado | agente=%d", codAgente);
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    private String chaveResumo(int codAgente, String mesAno) {
        return String.format(PADRAO_CHAVE_DADOS, codAgente, mesAno);
    }

    private String chaveLock(int codAgente) {
        return String.format(PADRAO_CHAVE_LOCK, codAgente);
    }

    // =========================================================================
    // EXCEÇÃO INTERNA DO REPOSITÓRIO
    // =========================================================================

    public static class RedisRepositoryException extends RuntimeException {
        public RedisRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
