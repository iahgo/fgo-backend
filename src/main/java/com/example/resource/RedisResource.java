package com.example.resource;

import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * GET /admin/redis         — lista todas as chaves com TTL e tamanho
 * GET /admin/redis/{chave} — retorna o valor bruto de uma chave
 * DELETE /admin/redis/{chave} — remove uma chave (força reload na próxima requisição)
 */
@Path("/admin/redis")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin — Redis", description = "Inspeciona e gerencia o cache Redis.")
public class RedisResource {

    private static final Logger LOG = Logger.getLogger(RedisResource.class);

    @Inject RedisDataSource redis;
    @Inject RedisClient redisClient;

    // =========================================================================
    // GET /admin/redis — visão geral de todas as chaves
    // =========================================================================

    @GET
    @Operation(
        summary = "Lista todas as chaves do Redis com TTL e tamanho",
        description = "Retorna metadados de cada chave: TTL restante, tamanho em bytes e tipo. " +
                      "Não retorna o valor (use GET /admin/redis/{chave} para isso)."
    )
    public Map<String, Object> listarChaves() {
        KeyCommands<String>   keys   = redis.key(String.class);
        ValueCommands<String, String> values = redis.value(String.class);

        List<String> todasChaves = keys.keys("*");
        todasChaves.sort(String::compareTo);

        List<Map<String, Object>> itens = new ArrayList<>();
        long totalBytes = 0;

        for (String chave : todasChaves) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chave", chave);

            // TTL
            long ttl = keys.ttl(chave);
            item.put("ttlSegundos", ttl);
            item.put("ttlHumano", ttl < 0 ? "sem expiração" : formatTtl(ttl));

            // Tamanho do valor em bytes (STRLEN para strings)
            try {
                long tamanho = redisClient.strlen(chave).toLong();
                item.put("tamanhoBytes", tamanho);
                item.put("tamanhoKb", Math.round(tamanho / 1024.0 * 100) / 100.0);
                totalBytes += tamanho;
            } catch (Exception e) {
                item.put("tamanhoBytes", -1);
            }

            itens.add(item);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("totalChaves", todasChaves.size());
        resultado.put("totalKb", Math.round(totalBytes / 1024.0 * 100) / 100.0);
        resultado.put("chaves", itens);
        return resultado;
    }

    // =========================================================================
    // GET /admin/redis/{chave} — valor de uma chave específica
    // =========================================================================

    @GET
    @Path("/{chave}")
    @Operation(
        summary = "Retorna o valor de uma chave Redis",
        description = "O valor é retornado como JSON se for válido, ou como string bruta. " +
                      "Use a chave exatamente como listada em GET /admin/redis."
    )
    public Map<String, Object> getChave(
            @Parameter(description = "Chave Redis (ex: af:1:operacao:resumo:2026-04)", required = true)
            @PathParam("chave") String chave) {

        KeyCommands<String> keys = redis.key(String.class);

        if (!keys.exists(chave)) {
            throw new NotFoundException("Chave não encontrada: " + chave);
        }

        String valor = redis.value(String.class).get(chave);
        long ttl     = keys.ttl(chave);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("chave", chave);
        resultado.put("ttlSegundos", ttl);
        resultado.put("ttlHumano", ttl < 0 ? "sem expiração" : formatTtl(ttl));
        resultado.put("tamanhoBytes", valor != null ? valor.length() : 0);

        // Tenta parsear como JSON para exibir estruturado
        if (valor != null && valor.trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                resultado.put("valor", mapper.readValue(valor, Object.class));
            } catch (Exception e) {
                resultado.put("valor", valor);
            }
        } else {
            resultado.put("valor", valor);
        }

        return resultado;
    }

    // =========================================================================
    // DELETE /admin/redis/{chave} — remove uma chave (força reload)
    // =========================================================================

    @DELETE
    @Path("/{chave}")
    @Operation(
        summary = "Remove uma chave do Redis",
        description = "Apaga a chave do cache. Na próxima requisição do agente, " +
                      "o warm-up será disparado automaticamente para recarregar do DB2."
    )
    public Map<String, Object> deletarChave(
            @Parameter(description = "Chave a remover (ex: af:1:operacao:resumo:2026-04)", required = true)
            @PathParam("chave") String chave) {

        long removidas = redis.key(String.class).del(chave);
        LOG.infof("[REDIS-ADMIN] Chave removida manualmente: %s", chave);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("chave", chave);
        resultado.put("removida", removidas > 0);
        resultado.put("mensagem", removidas > 0
                ? "Chave removida. O warm-up será disparado na próxima requisição do agente."
                : "Chave não encontrada.");
        return resultado;
    }

    // =========================================================================
    // DELETE /admin/redis — limpa TODO o cache (força reload geral)
    // =========================================================================

    @DELETE
    @Operation(
        summary = "Limpa todo o cache Redis",
        description = "⚠️ Remove todas as chaves. Os agentes vão recarregar do DB2 na próxima requisição. " +
                      "Use POST /api/operacoes/admin/reload para recarregar proativamente."
    )
    public Map<String, Object> limparTudo() {
        KeyCommands<String> keys = redis.key(String.class);
        List<String> todasChaves = keys.keys("*");
        long removidas = 0;
        if (!todasChaves.isEmpty()) {
            removidas = keys.del(todasChaves.toArray(new String[0]));
        }
        LOG.infof("[REDIS-ADMIN] Cache limpo manualmente: %d chaves removidas", removidas);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("removidas", removidas);
        resultado.put("mensagem", "Cache limpo. Use POST /api/operacoes/admin/reload para recarregar.");
        return resultado;
    }

    private String formatTtl(long segundos) {
        if (segundos < 0) return "sem expiração";
        long h = segundos / 3600;
        long m = (segundos % 3600) / 60;
        long s = segundos % 60;
        return String.format("%dh %02dm %02ds", h, m, s);
    }
}
