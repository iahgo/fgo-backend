package com.example.resource;

import com.example.service.SeedService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Endpoint administrativo para geração de dados de teste em massa no DB2.
 *
 * POST /admin/seed?quantidade=35000000&limpar=true  → 202 Accepted (execução assíncrona)
 * POST /admin/seed/remessas                         → 202 Accepted (só remessas, não apaga operações)
 * GET  /admin/seed/status                           → JSON com progresso atual
 */
@Path("/admin/seed")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Tag(
    name = "Admin — Seed de Dados",
    description = """
        **ATENÇÃO — ENDPOINTS DESTRUTIVOS**

        Os endpoints abaixo operam sobre as tabelas reais do DB2.
        Leia a descrição de cada um antes de executar.

        - `POST /admin/seed` — **APAGA TUDO** e recria com dados sintéticos.
          Perda total dos 100M de registros de operações. Irreversível.
        - `POST /admin/seed/remessas` — Apaga e recria APENAS a tabela de remessas.
          Operações (OPR_CRD_FNDO_GRTR) são preservadas.
        - `GET /admin/seed/status` — Somente leitura. Seguro.
        """
)
public class SeedResource {

    private static final Logger LOG = Logger.getLogger(SeedResource.class);

    @Inject
    SeedService seedService;

    @Inject
    ManagedExecutor executor;

    // =========================================================================
    // POST /admin/seed — FULL SEED (DESTRUTIVO)
    // =========================================================================

    @POST
    @Operation(
        summary = "DESTRUTIVO — Apaga tudo e regera dados sintéticos",
        description = """
            ⚠️ **OPERAÇÃO IRREVERSÍVEL — NÃO EXECUTE EM PRODUÇÃO**

            Com `limpar=true` (padrão):
            - **TRUNCATE** em `OPR_CRD_FNDO_GRTR` (100M+ registros — perda total)
            - **DELETE** em todas as tabelas de domínio (agentes, fundos, programas…)
            - Reinserção dos dados mestres
            - Geração de `quantidade` novas operações sintéticas

            Com `limpar=false`:
            - Apenas insere novas operações sem apagar as existentes.

            A execução ocorre em background — o endpoint retorna 202 imediatamente.
            Acompanhe via `GET /admin/seed/status`.

            **Impacto:** O warm-up do Redis será necessário após a conclusão
            (`POST /api/operacoes/admin/reload`).
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Geração iniciada em background"),
        @APIResponse(responseCode = "400", description = "Quantidade fora do intervalo permitido"),
        @APIResponse(responseCode = "409", description = "Outra geração já está em andamento")
    })
    public Response iniciar(
            @Parameter(description = "Número de operações a gerar (1 a 500.000.000)")
            @QueryParam("quantidade") @DefaultValue("1000000") long quantidade,

            @Parameter(description = "**true** = apaga TODOS os dados antes (IRREVERSÍVEL). false = apenas insere.")
            @QueryParam("limpar") @DefaultValue("true") boolean limpar) {

        if (seedService.isEmExecucao()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "erro",      "Geração já em andamento",
                            "progresso", seedService.getProgresso(),
                            "total",     seedService.getTotal(),
                            "status",    seedService.getStatusMsg()
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
                "mensagem",   "Geração iniciada em background",
                "quantidade", qtd,
                "limpar",     lmp,
                "aviso",      limpar ? "TODOS os dados existentes serão apagados!" : "Inserção sem limpeza",
                "statusUrl",  "/admin/seed/status"
        )).build();
    }

    // =========================================================================
    // POST /admin/seed/remessas — SEED DE REMESSAS (preserva operações)
    // =========================================================================

    @POST
    @Path("/remessas")
    @Operation(
        summary = "TRUNCATE remessas — popula RMS_AGT_FNCO (preserva operações)",
        description = """
            ⚠️ **Apaga e recria a tabela de remessas (RMS_AGT_FNCO)**

            - Faz **TRUNCATE** apenas em `RMS_AGT_FNCO`
            - **Preserva** os 100M de registros de `OPR_CRD_FNDO_GRTR`
            - Insere 1 remessa por agente × fundo × dia de 2024-01-01 até hoje
            - Total esperado: ~11.800 registros

            A execução ocorre em background — o endpoint retorna 202 imediatamente.
            Acompanhe via `GET /admin/seed/status`.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Geração de remessas iniciada em background"),
        @APIResponse(responseCode = "409", description = "Outra geração já está em andamento")
    })
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
                "aviso",     "Apenas RMS_AGT_FNCO será truncada. OPR_CRD_FNDO_GRTR preservada.",
                "statusUrl", "/admin/seed/status"
        )).build();
    }

    // =========================================================================
    // GET /admin/seed/status — somente leitura
    // =========================================================================

    @GET
    @Path("/status")
    @Operation(
        summary = "Progresso da geração de dados",
        description = "Somente leitura. Retorna o status atual da geração em background."
    )
    @APIResponse(responseCode = "200", description = "Status retornado")
    public Response status() {
        long prog  = seedService.getProgresso();
        long total = seedService.getTotal();
        double pct = total > 0 ? (prog * 100.0 / total) : 0.0;

        return Response.ok(Map.of(
                "emExecucao", seedService.isEmExecucao(),
                "progresso",  prog,
                "total",      total,
                "percentual", String.format("%.2f%%", pct),
                "status",     seedService.getStatusMsg()
        )).build();
    }
}
