package com.example.service;

import com.example.dto.AgenteDto;
import com.example.repository.OperacaoRepository;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço para listagem de agentes financeiros cadastrados.
 * totalOperacoes é cacheado no Redis (TTL 1h) — evita GROUP BY em 100M registros a cada request.
 */
@ApplicationScoped
public class AgenteService {

    private static final Logger LOG = Logger.getLogger(AgenteService.class);
    private static final String CACHE_KEY = "cache:agentes:contagens";

    @Inject
    OperacaoRepository repository;

    @Inject
    RedisDataSource redis;

    /**
     * Retorna todos os agentes com totalOperacoes.
     * Contagens cacheadas no Redis por 1h — primeira chamada após cold start leva ~2min,
     * as seguintes retornam em < 100ms.
     */
    public List<AgenteDto> listarTodos() {
        Map<Integer, Long> contagens = carregarContagensCache();

        return repository.buscarTodosAgentes().stream()
                .map(a -> new AgenteDto(
                        a.getCdAgtFnco(),
                        a.getNmAbvdAgtFnco().trim(),
                        contagens.getOrDefault(a.getCdAgtFnco(), 0L)))
                .toList();
    }

    /** Invalida o cache de contagens (chamado após seed de operações). */
    public void invalidarCache() {
        redis.key(String.class).del(CACHE_KEY);
        LOG.info("[AGENTE-SVC] Cache de contagens invalidado.");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> carregarContagensCache() {
        ValueCommands<String, String> values = redis.value(String.class);
        String cached = values.get(CACHE_KEY);

        if (cached != null) {
            // Deserializa "cod1:total1,cod2:total2,..."
            Map<Integer, Long> map = new HashMap<>();
            for (String par : cached.split(",")) {
                String[] kv = par.split(":");
                if (kv.length == 2) map.put(Integer.parseInt(kv[0]), Long.parseLong(kv[1]));
            }
            LOG.debugf("[AGENTE-SVC] Contagens lidas do cache Redis (%d agentes).", map.size());
            return map;
        }

        // Cache miss — busca no DB2 (lento, ~2min em 100M registros)
        LOG.info("[AGENTE-SVC] Cache miss — consultando DB2 para contagens (pode demorar)...");
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : repository.contarOperacoesPorAgente()) {
            map.put((Integer) row[0], (Long) row[1]);
        }

        // Serializa e salva com TTL 1h
        if (!map.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> sb.append(k).append(':').append(v).append(','));
            sb.setLength(sb.length() - 1); // remove última vírgula
            values.setex(CACHE_KEY, 3600L, sb.toString());
            LOG.infof("[AGENTE-SVC] Contagens salvas no Redis (TTL 1h, %d agentes).", map.size());
        }

        return map;
    }
}
