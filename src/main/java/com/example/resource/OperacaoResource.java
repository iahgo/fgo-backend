package com.example.resource;

import com.example.dto.ConsolidadoDto;
import com.example.dto.ErroDto;
import com.example.dto.OperacaoResumoDto;
import com.example.loader.OperacaoLoader;
import com.example.service.ConsolidadoService;
import com.example.service.OperacaoService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.context.ManagedExecutor;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Endpoint REST do MS Operação.
 *
 * FLUXO COMPLETO DE UMA REQUISIÇÃO (conforme arquitetura FGO):
 *
 *   1. Funcionário do Itaú clica em "Ver Operações" no Angular
 *   2. Angular envia GET /api/operacoes/2025-04
 *      com header Authorization: Bearer <token-de-sessão>
 *   3. IIB (gateway corporativo BB) recebe:
 *      - Valida o token de sessão via Conta Acesso BB
 *      - Resolve cod_agente = 8 (Itaú) da identidade autenticada
 *      - Gera token de atendimento (JWT) com escopo "operacao:read"
 *      - Injeta header X-Cod-Agente: 8
 *      - Repassa para o MS via rede interna do OpenShift/Docker
 *   4. Este endpoint recebe a requisição interna:
 *      - Lê X-Cod-Agente: 8 (NUNCA de query param ou body — regra de segurança)
 *      - Chama OperacaoService.getResumo(8, "2025-04")
 *      - Service faz Redis.GET "af:8:operacao:resumo:2025-04"
 *      - Retorna em < 1ms
 *      - DB2 não é tocado
 *
 * SEGURANÇA:
 *   O cod_agente vem EXCLUSIVAMENTE do header X-Cod-Agente injetado pelo IIB.
 *   Nunca de path param, query param ou body.
 *   Isso impede que um funcionário do Itaú forje cod_agente=18 para ver
 *   dados do Bradesco — o IIB sempre sobrescreve com o código real da
 *   identidade autenticada.
 *
 * SIMULAÇÃO SEM IIB (dev/testes):
 *   Em ambiente local (sem IIB), passe o header manualmente:
 *     curl -H "X-Cod-Agente: 8" http://localhost:8080/api/operacoes/2025-04
 */
@Path("/api/operacoes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operações", description = "KPIs agregados de operações de crédito por agente financeiro")
public class OperacaoResource {

    private static final Logger LOG = Logger.getLogger(OperacaoResource.class);

    @Inject
    OperacaoService service;

    @Inject
    ConsolidadoService consolidadoService;

    @Inject
    OperacaoLoader loader;

    @Inject
    ManagedExecutor executor;

    // =====================================================================
    // ENDPOINT PRINCIPAL: GET /api/operacoes/{mesAno}
    // =====================================================================

    @GET
    @Path("/{mesAno}")
    @Operation(
        summary = "KPIs de operações do mês",
        description = """
            Retorna os KPIs agregados de operações de crédito do agente financeiro
            para o mês especificado.

            O cod_agente é resolvido automaticamente pelo IIB a partir do token
            de sessão — não é passado pelo Angular.

            Em desenvolvimento (sem IIB): passe o header X-Cod-Agente manualmente.

            Dados servidos 100% do Redis. DB2 não é consultado nesta operação.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "KPIs do agente retornados com sucesso"),
        @APIResponse(responseCode = "400", description = "Formato de mesAno inválido ou header X-Cod-Agente ausente",
            content = @Content(schema = @Schema(implementation = ErroDto.class))),
        @APIResponse(responseCode = "403", description = "Agente não habilitado no FGO",
            content = @Content(schema = @Schema(implementation = ErroDto.class))),
        @APIResponse(responseCode = "503", description = "Dados ainda não disponíveis no Redis (warm-up em andamento ou falha no loader)",
            headers = @Header(name = "Retry-After", description = "Segundos até nova tentativa"),
            content = @Content(schema = @Schema(implementation = ErroDto.class)))
    })
    public OperacaoResumoDto getResumo(
            @Parameter(
                description = "Mês de referência no formato YYYY-MM (ex: 2025-04)",
                required = true, example = "2025-04"
            )
            @PathParam("mesAno")
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato inválido. Use YYYY-MM (ex: 2025-04).")
            String mesAno,

            @Parameter(
                description = "Código interno do agente financeiro. " +
                              "Em produção é injetado pelo IIB a partir do token de sessão. " +
                              "Em desenvolvimento, passe manualmente via header.",
                required = true, example = "8"
            )
            @HeaderParam("X-Cod-Agente") Integer codAgente) {

        if (codAgente == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErroDto("HEADER_AUSENTE",
                                "Header X-Cod-Agente obrigatório. Em produção é injetado pelo IIB."))
                        .build()
            );
        }

        LOG.debugf("[RESOURCE] GET /api/operacoes/%s | agente=%d", mesAno, codAgente);

        // Exceções de negócio (agente não habilitado, dados ausentes) são lançadas
        // pelo service e tratadas pelo GlobalExceptionMapper — sem try/catch aqui.
        return service.getResumo(codAgente, mesAno);
    }

    // =====================================================================
    // ENDPOINT CONSOLIDADO: GET /api/operacoes/{mesAno}/consolidado
    // =====================================================================

    /**
     * Visão agregada de TODOS os agentes para o mês.
     *
     * Retorna totais gerais + breakdown por programa e por agente.
     * Consulta o DB2 diretamente — não usa Redis.
     * Tempo de resposta esperado: 10–60s (query pesada em 100M+ registros).
     *
     * Em produção, requer ROLE_GESTOR_FGO.
     * Em desenvolvimento: curl http://localhost:8080/api/operacoes/2026-04/consolidado
     */
    @GET
    @Path("/{mesAno}/consolidado")
    @Operation(
        summary = "Visão consolidada de todos os agentes",
        description = """
            Retorna KPIs agregados de todos os agentes financeiros para o mês.
            Inclui totais gerais, breakdown por programa de crédito e ranking por agente.
            Consulta o DB2 diretamente — pode demorar até 60s em volumes de produção.
            Em produção requer ROLE_GESTOR_FGO.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Consolidado retornado com sucesso"),
        @APIResponse(responseCode = "400", description = "Formato de mesAno inválido",
            content = @Content(schema = @Schema(implementation = ErroDto.class)))
    })
    public ConsolidadoDto getConsolidado(
            @Parameter(
                description = "Mês de referência no formato YYYY-MM",
                required = true, example = "2026-04"
            )
            @PathParam("mesAno")
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato inválido. Use YYYY-MM.")
            String mesAno) {

        LOG.infof("[RESOURCE] GET /api/operacoes/%s/consolidado", mesAno);
        return consolidadoService.getConsolidado(mesAno);
    }

    // =====================================================================
    // ENDPOINT DE RELOAD: POST /api/operacoes/admin/reload
    // =====================================================================

    /**
     * Força recarga do Redis para TODOS os agentes do domínio operação.
     *
     * Caso de uso: ETL foi reprocessado fora do horário normal (ex: às 14h) e o gestor
     * precisa que todos os agentes reflitam os dados corrigidos imediatamente.
     *
     * A recarga roda em background — o endpoint retorna 202 imediatamente.
     * O tempo real de execução (todos os agentes) pode levar vários minutos.
     *
     * Em produção este endpoint é acionado indiretamente pelo MS Administração via
     * Redis Pub/Sub (canal fgo:admin:reload, mensagem "operacao" ou "todos").
     * O subscriber {@link com.example.listener.OperacaoReloadSubscriber} também
     * chama o mesmo loader, garantindo comportamento idêntico em ambos os fluxos.
     *
     * Protegido por ROLE_GESTOR_FGO no JWT de atendimento do IIB (produção).
     * Em desenvolvimento local (sem IIB): curl -X POST localhost:8080/api/operacoes/admin/reload
     */
    @POST
    @Path("/admin/reload")
    @Operation(
        summary = "Força recarga do Redis — todos os agentes",
        description = """
            Recarrega o cache Redis de todos os agentes financeiros para o domínio operação.
            Retorna 202 imediatamente. A recarga ocorre em background.
            Em produção, requer ROLE_GESTOR_FGO no JWT de atendimento do IIB.
            """
    )
    @APIResponse(responseCode = "202", description = "Recarga iniciada em background — todos os agentes")
    public Response recarregarTodos() {
        LOG.info("[RESOURCE] Reload de todos os agentes solicitado via endpoint.");
        executor.submit(loader::recarregarTudo);
        return Response.accepted(new ErroDto("RELOAD_INICIADO",
                "Recarga de todos os agentes iniciada em background.")).build();
    }

    // =====================================================================
    // ENDPOINT DE RELOAD: POST /api/operacoes/admin/reload/{codAgente}
    // =====================================================================

    /**
     * Força recarga do Redis para UM agente específico.
     *
     * Caso de uso: reprocessamento pontual de um único agente às 14h — sem precisar
     * recarregar os ~40 agentes do domínio inteiro. Execução muito mais rápida.
     *
     * A recarga usa o mesmo lock distribuído SET NX do warm-up normal, garantindo
     * que apenas 1 pod execute o carregamento mesmo em ambientes com múltiplas réplicas.
     *
     * Retorna 202 imediatamente. A carga ocorre em background.
     *
     * Protegido por ROLE_GESTOR_FGO no JWT de atendimento do IIB (produção).
     * Em desenvolvimento: curl -X POST localhost:8080/api/operacoes/admin/reload/8
     */
    @POST
    @Path("/admin/reload/{codAgente}")
    @Operation(
        summary = "Força recarga do Redis — agente específico",
        description = """
            Recarrega o cache Redis de um único agente financeiro.
            Mais rápido que o reload completo — útil para reprocessamentos pontuais.
            Retorna 202 imediatamente. A recarga ocorre em background.
            Em produção, requer ROLE_GESTOR_FGO no JWT de atendimento do IIB.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Recarga do agente iniciada em background"),
        @APIResponse(responseCode = "400", description = "Código de agente inválido")
    })
    public Response recarregarAgente(@PathParam("codAgente") int codAgente) {
        LOG.infof("[RESOURCE] Reload solicitado para agente=%d via endpoint.", codAgente);
        executor.submit(() -> loader.recarregarAgente(codAgente));
        return Response.accepted(new ErroDto("RELOAD_INICIADO",
                "Recarga do agente " + codAgente + " iniciada em background.")).build();
    }
}
