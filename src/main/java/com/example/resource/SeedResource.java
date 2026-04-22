package com.example.resource;

import com.example.service.SeedService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Endpoint administrativo para geração de dados de teste em massa no DB2.
 *
 * POST /admin/seed?quantidade=35000000&limpar=true  → 202 Accepted (execução assíncrona)
 * GET  /admin/seed/status                           → JSON com progresso atual
 */
@Path("/admin/seed")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class SeedResource {

    private static final Logger LOG = Logger.getLogger(SeedResource.class);

    @Inject
    SeedService seedService;

    @Inject
    ManagedExecutor executor;

    /**
     * Dispara a geração de dados em background.
     *
     * @param quantidade número de operações a gerar (padrão: 1_000_000)
     * @param limpar     se true, apaga todos os dados antes de gerar (padrão: true)
     */
    @POST
    public Response iniciar(
            @QueryParam("quantidade") @DefaultValue("1000000") long quantidade,
            @QueryParam("limpar")     @DefaultValue("true")    boolean limpar) {

        if (seedService.isEmExecucao()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "erro", "Geração já em andamento",
                            "progresso", seedService.getProgresso(),
                            "total", seedService.getTotal(),
                            "status", seedService.getStatusMsg()
                    ))
                    .build();
        }

        if (quantidade <= 0 || quantidade > 500_000_000L) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "quantidade deve estar entre 1 e 500.000.000"))
                    .build();
        }

        LOG.infof("[SEED] Iniciando geração: quantidade=%d limpar=%b", quantidade, limpar);

        final long qtd    = quantidade;
        final boolean lmp = limpar;
        executor.submit(() -> {
            try {
                seedService.gerarDados(qtd, lmp);
            } catch (Exception e) {
                LOG.errorf(e, "[SEED] Falha durante geração de dados");
            }
        });

        return Response.accepted(Map.of(
                "mensagem",  "Geração iniciada em background",
                "quantidade", qtd,
                "limpar",     lmp,
                "statusUrl",  "/admin/seed/status"
        )).build();
    }

    /**
     * Popula apenas a tabela RMS_AGT_FNCO com remessas diárias (2024-01-01 até hoje).
     * NÃO apaga nem toca nas operações (OPR_CRD_FNDO_GRTR).
     * Seguro de usar mesmo com 100M de operações já inseridas.
     */
    @POST
    @Path("/remessas")
    public Response gerarRemessas() {
        if (seedService.isEmExecucao()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("erro", "Geração já em andamento", "status", seedService.getStatusMsg()))
                    .build();
        }

        LOG.info("[SEED] Iniciando seed de remessas (sem apagar operações).");
        executor.submit(() -> {
            try {
                seedService.gerarSomenteRemessas();
            } catch (Exception e) {
                LOG.errorf(e, "[SEED] Falha ao gerar remessas");
            }
        });

        return Response.accepted(Map.of(
                "mensagem",  "Geração de remessas iniciada em background (~11.800 registros)",
                "statusUrl", "/admin/seed/status"
        )).build();
    }

    /**
     * Retorna o progresso atual da geração de dados.
     */
    @GET
    @Path("/status")
    public Response status() {
        long prog  = seedService.getProgresso();
        long total = seedService.getTotal();
        double pct = total > 0 ? (prog * 100.0 / total) : 0.0;

        return Response.ok(Map.of(
                "emExecucao",  seedService.isEmExecucao(),
                "progresso",   prog,
                "total",       total,
                "percentual",  String.format("%.2f%%", pct),
                "status",      seedService.getStatusMsg()
        )).build();
    }
}
