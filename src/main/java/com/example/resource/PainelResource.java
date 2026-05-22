package com.example.resource;

import com.example.dto.painel.InadimplenciaDto;
import com.example.dto.painel.InformacoesGeraisDto;
import com.example.dto.painel.IvhItemDto;
import com.example.dto.painel.IvhSerieDto;
import com.example.dto.painel.MovimentacaoSerieDto;
import com.example.dto.painel.PendenciasResumoDto;
import com.example.dto.painel.RemessasResumoDto;
import com.example.service.PainelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Resource REST para os endpoints do painel do agente financeiro.
 *
 * Endpoints:
 *   GET /api/v1/painel/informacoes-gerais
 *   GET /api/v1/painel/inadimplencia
 *   GET /api/v1/painel/ivh
 *   GET /api/v1/painel/ivh/serie-historica
 *   GET /api/v1/painel/remessas/resumo
 *   GET /api/v1/painel/pendencias/resumo
 *   GET /api/v1/painel/movimentacao-financeira/serie-historica
 *
 * TODO: substituir @QueryParam("cdAgtFnco") por contexto JWT quando a autenticação for implementada.
 */
@Path("/api/v1/painel")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Painel", description = "KPIs e resumos do painel do agente financeiro")
public class PainelResource {

    private static final Logger LOG = Logger.getLogger(PainelResource.class);

    @Inject
    PainelService painelService;

    // =========================================================================
    // Informações Gerais — endpoint 3
    // =========================================================================

    @GET
    @Path("/informacoes-gerais")
    @Operation(summary = "KPIs de informações gerais",
            description = "Retorna mutuários, operações, saldo contratado, ticket médio e saldo carteira.")
    public InformacoesGeraisDto informacoesGerais(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo,
            @QueryParam("cdPrograma") @DefaultValue("-1") int cdPrograma) {
        LOG.debugf("[PAINEL-RES] informacoesGerais agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);
        return painelService.buscarInformacoesGerais(cdAgtFnco, cdFundo, cdPrograma);
    }

    // =========================================================================
    // Inadimplência — endpoint 4
    // =========================================================================

    @GET
    @Path("/inadimplencia")
    @Operation(summary = "Dados de inadimplência",
            description = "Retorna saldo atrasado, índice de inadimplência por saldo e por operações.")
    public InadimplenciaDto inadimplencia(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo,
            @QueryParam("cdPrograma") @DefaultValue("-1") int cdPrograma) {
        LOG.debugf("[PAINEL-RES] inadimplencia agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);
        return painelService.buscarInadimplencia(cdAgtFnco, cdFundo, cdPrograma);
    }

    // =========================================================================
    // IVH por Programa — endpoint 5
    // =========================================================================

    @GET
    @Path("/ivh")
    @Operation(summary = "Tabela IVH por programa",
            description = "Retorna cobertura, valores honrados, recuperados, contratado e IVH por programa de crédito.")
    public List<IvhItemDto> ivh(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        LOG.debugf("[PAINEL-RES] ivh agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.buscarIvh(cdAgtFnco, cdFundo);
    }

    // =========================================================================
    // IVH Série Histórica — endpoint 6
    // =========================================================================

    @GET
    @Path("/ivh/serie-historica")
    @Operation(summary = "Série histórica do IVH",
            description = "Retorna a evolução mensal do IVH no formato [{periodo:'yyyy-MM', ivh:3.21}].")
    public IvhSerieDto ivhSerieHistorica(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        LOG.debugf("[PAINEL-RES] ivhSerieHistorica agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.buscarIvhSerieHistorica(cdAgtFnco, cdFundo);
    }

    // =========================================================================
    // Remessas Resumo — endpoint 7
    // =========================================================================

    @GET
    @Path("/remessas/resumo")
    @Operation(summary = "Resumo de remessas",
            description = "Retorna total esperadas, não movimentadas e concluídas.")
    public RemessasResumoDto remessasResumo(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        LOG.debugf("[PAINEL-RES] remessasResumo agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.buscarRemessasResumo(cdAgtFnco, cdFundo);
    }

    // =========================================================================
    // Pendências Resumo — endpoint 8
    // =========================================================================

    @GET
    @Path("/pendencias/resumo")
    @Operation(summary = "Resumo de pendências",
            description = "Retorna grupos de pendências com tipo, label e valor agregado.")
    public PendenciasResumoDto pendenciasResumo(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        LOG.debugf("[PAINEL-RES] pendenciasResumo agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.buscarPendenciasResumo(cdAgtFnco, cdFundo);
    }

    // =========================================================================
    // Movimentação Financeira Série Histórica — endpoint 9
    // =========================================================================

    @GET
    @Path("/movimentacao-financeira/serie-historica")
    @Operation(summary = "Série histórica de movimentação financeira",
            description = "Retorna evolução mensal da carteira do agente e do fundo.")
    public MovimentacaoSerieDto movimentacaoSerieHistorica(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco,
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        LOG.debugf("[PAINEL-RES] movimentacaoSerieHistorica agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.buscarMovimentacaoSerie(cdAgtFnco, cdFundo);
    }
}
