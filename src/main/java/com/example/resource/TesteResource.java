package com.example.resource;

import com.example.repository.OperacaoRedisRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/admin/teste")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Teste", description = "Endpoints de teste — remover antes de ir para producao")
public class TesteResource {

    private static final Logger LOG = Logger.getLogger(TesteResource.class);

    @Inject OperacaoRedisRepository redisRepository;

    @POST
    @Path("/setnx/{codAgente}")
    @Operation(
        summary = "Testa lock distribuido SETNX",
        description = "Tenta adquirir o lock do agente, simula uma query de 5 segundos e libera. " +
                      "Chame simultaneamente nos 3 pods para ver qual adquire e quais sao bloqueados. " +
                      "HOSTNAME identifica o pod respondente."
    )
    public Response testeSetnx(@PathParam("codAgente") int codAgente) {
        String pod = System.getenv("HOSTNAME");
        long inicio = System.currentTimeMillis();

        LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | tentando adquirir lock...", pod, codAgente);

        boolean adquiriu = redisRepository.adquirirLock(codAgente);

        if (!adquiriu) {
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | LOCK OCUPADO — outro pod esta executando", pod, codAgente);
            return Response.ok(Map.of(
                "pod", pod,
                "agente", codAgente,
                "status", "LOCK_OCUPADO",
                "mensagem", "Outro pod ja adquiriu o lock — SETNX funcionando corretamente",
                "duracaoMs", System.currentTimeMillis() - inicio
            )).build();
        }

        try {
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | LOCK ADQUIRIDO — simulando query de 5s...", pod, codAgente);
            Thread.sleep(5000);
            long duracao = System.currentTimeMillis() - inicio;
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | query concluida em %dms", pod, codAgente, duracao);

            return Response.ok(Map.of(
                "pod", pod,
                "agente", codAgente,
                "status", "EXECUTOU",
                "mensagem", "Lock adquirido e query simulada com sucesso",
                "duracaoMs", duracao
            )).build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.serverError().entity(Map.of(
                "pod", pod,
                "status", "ERRO",
                "mensagem", "Thread interrompida"
            )).build();
        } finally {
            redisRepository.liberarLock(codAgente);
            LOG.infof("[TESTE-SETNX] pod=%s | agente=%d | lock liberado", pod, codAgente);
        }
    }
}
