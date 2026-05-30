package com.example.resource;

import com.example.repository.OperacaoRedisRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/admin/teste")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Teste", description = "Endpoints de teste — remover antes de ir para producao")
public class TesteResource {

    private static final Logger LOG = Logger.getLogger(TesteResource.class);
    private static final String POD = System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local";
    private static final List<Integer> AGENTES_TESTE = List.of(1, 2, 3);

    @Inject OperacaoRedisRepository redisRepository;

    @Scheduled(every = "2m")
    public void schedulerSetnx() {
        LOG.infof("=== [TESTE-SCHEDULER] pod=%s | iniciando ciclo — agentes=%s ===", POD, AGENTES_TESTE);

        for (int codAgente : AGENTES_TESTE) {
            boolean adquiriu = redisRepository.adquirirLock(codAgente);

            if (!adquiriu) {
                LOG.infof("[TESTE-SCHEDULER] pod=%s | agente=%d | LOCK OCUPADO — outro pod esta processando, pulando.", POD, codAgente);
                continue;
            }

            try {
                LOG.infof("[TESTE-SCHEDULER] pod=%s | agente=%d | LOCK ADQUIRIDO — simulando query de 5s...", POD, codAgente);
                Thread.sleep(5000);
                LOG.infof("[TESTE-SCHEDULER] pod=%s | agente=%d | query concluida.", POD, codAgente);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.errorf("[TESTE-SCHEDULER] pod=%s | agente=%d | thread interrompida.", POD, codAgente);
            } finally {
                redisRepository.liberarLock(codAgente);
            }
        }

        LOG.infof("=== [TESTE-SCHEDULER] pod=%s | ciclo concluido ===", POD);
    }

    @POST
    @Path("/setnx/{codAgente}")
    @Operation(
        summary = "Testa lock distribuido SETNX",
        description = "Tenta adquirir o lock do agente, simula uma query de 5 segundos e libera. " +
                      "Chame simultaneamente nos 3 pods para ver qual adquire e quais sao bloqueados."
    )
    public Response testeSetnx(@PathParam("codAgente") int codAgente) {
        long inicio = System.currentTimeMillis();
        LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | tentando adquirir lock...", POD, codAgente);

        boolean adquiriu = redisRepository.adquirirLock(codAgente);

        if (!adquiriu) {
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | LOCK OCUPADO — outro pod esta executando", POD, codAgente);
            return Response.ok(Map.of(
                "pod", POD,
                "agente", codAgente,
                "status", "LOCK_OCUPADO",
                "mensagem", "Outro pod ja adquiriu o lock — SETNX funcionando corretamente",
                "duracaoMs", System.currentTimeMillis() - inicio
            )).build();
        }

        try {
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | LOCK ADQUIRIDO — simulando query de 5s...", POD, codAgente);
            Thread.sleep(5000);
            long duracao = System.currentTimeMillis() - inicio;
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | query concluida em %dms", POD, codAgente, duracao);
            return Response.ok(Map.of(
                "pod", POD,
                "agente", codAgente,
                "status", "EXECUTOU",
                "mensagem", "Lock adquirido e query simulada com sucesso",
                "duracaoMs", duracao
            )).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.serverError().entity(Map.of(
                "pod", POD,
                "status", "ERRO",
                "mensagem", "Thread interrompida"
            )).build();
        } finally {
            redisRepository.liberarLock(codAgente);
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | lock liberado", POD, codAgente);
        }
    }
}
