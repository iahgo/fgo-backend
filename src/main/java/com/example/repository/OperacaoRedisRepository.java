package com.example.repository;

import com.example.config.FgoConfig;
import com.example.dto.OperacaoResumoDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class OperacaoRedisRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRedisRepository.class);
    private static final String PADRAO_CHAVE_DADOS = "af:%d:operacao:resumo";
    private static final String PADRAO_CHAVE_LOCK  = "lock:operacao:%d";

    @Inject RedisDataSource redis;
    @Inject FgoConfig config;
    @Inject ObjectMapper objectMapper;

    public void salvar(OperacaoResumoDto dto) {
        String chave = chaveResumo(dto.getCodigoAgente());
        try {
            String json = objectMapper.writeValueAsString(dto);
            ValueCommands<String, String> values = redis.value(String.class);
            values.set(chave, json);
            redis.key(String.class).expire(chave, config.cache().ttlDados());
            LOG.debugf("[REDIS] Salvo | chave=%s | TTL=%s", chave, config.cache().ttlDados());
        } catch (Exception e) {
            throw new RedisRepositoryException("Falha ao salvar no Redis | chave=" + chave, e);
        }
    }

    public Optional<OperacaoResumoDto> buscar(int codAgente) {
        String chave = chaveResumo(codAgente);
        try {
            String json = redis.value(String.class).get(chave);
            if (json == null) {
                LOG.debugf("[REDIS] Miss | chave=%s", chave);
                return Optional.empty();
            }
            LOG.debugf("[REDIS] Hit  | chave=%s", chave);
            return Optional.of(objectMapper.readValue(json, OperacaoResumoDto.class));
        } catch (Exception e) {
            throw new RedisRepositoryException("Falha ao buscar no Redis | chave=" + chave, e);
        }
    }

    public boolean existe(int codAgente) {
        String chave = chaveResumo(codAgente);
        String json = redis.value(String.class).get(chave);
        if (json == null) return false;
        try {
            OperacaoResumoDto dto = objectMapper.readValue(json, OperacaoResumoDto.class);
            return dto.getProgramas() != null && !dto.getProgramas().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean adquirirLock(int codAgente) {
        String chave = chaveLock(codAgente);
        boolean adquirido = redis.value(String.class).setnx(chave, "locked");
        if (adquirido) {
            redis.key(String.class).expire(chave, config.cache().ttlLock());
        }
        return adquirido;
    }

    public void liberarLock(int codAgente) {
        redis.key(String.class).del(chaveLock(codAgente));
    }

    private String chaveResumo(int codAgente) { return String.format(PADRAO_CHAVE_DADOS, codAgente); }
    private String chaveLock(int codAgente)   { return String.format(PADRAO_CHAVE_LOCK, codAgente); }

    public static class RedisRepositoryException extends RuntimeException {
        public RedisRepositoryException(String message, Throwable cause) { super(message, cause); }
    }
}
