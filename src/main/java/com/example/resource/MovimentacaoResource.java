package com.example.resource;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.MovimentacaoDetalheDto;
import com.example.dto.listagem.MovimentacaoItemDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.MovimentacaoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Resource REST para listagem, detalhe e exportação de movimentações financeiras.
 *
 * GET /api/v1/movimentacoes/filtros/situacoes-remessa — endpoint 16: filtros de situação
 * GET /api/v1/movimentacoes                           — endpoint 17: listagem paginada
 * GET /api/v1/movimentacoes/{nrSequencial}/detalhe    — endpoint 18: detalhe por ContaMovimentoGarantia
 * GET /api/v1/movimentacoes/exportar                  — endpoint 19: exportação CSV
 *
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/movimentacoes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Movimentações v1", description = "Listagem, detalhe e exportação de movimentações financeiras — fonte: DB2GFG.RMS_AGT_FNCO filtrado por CD_TIP_NTZ_MVTC IN (1,2) (query MVTC_FNCR do BI, custo 734)")
public class MovimentacaoResource {

    private static final Logger LOG = Logger.getLogger(MovimentacaoResource.class);

    @Inject
    MovimentacaoService movimentacaoService;

    @Inject
    ContextoSeguranca contexto;

    // =========================================================================
    // Filtros de situação de remessa — endpoint 16
    // =========================================================================

    @GET
    @Path("/filtros/situacoes-remessa")
    @Operation(summary = "Lista situações de remessa para filtro de movimentações",
            description = "Retorna os códigos e labels das situações de remessa disponíveis como filtro.")
    public List<FiltroItemDto> filtrosSituacoesRemessa() {
        return movimentacaoService.listarSituacoesRemessa();
    }

    // =========================================================================
    // Listagem paginada — endpoint 17
    // =========================================================================

    @GET
    @Funcionalidade("MOVIMENTACOES_LISTA")
    @Operation(summary = "Lista movimentações paginadas",
            description = "Retorna movimentações financeiras do agente com filtros opcionais, paginado.")
    public PageDto<MovimentacaoItemDto> listar(
            @QueryParam("cdFundo")          @DefaultValue("-1")  int cdFundo,
            @QueryParam("situacaoRemessa")  @DefaultValue("-1")  int situacaoRemessa,
            @QueryParam("nrSequencial")                          Short nrSequencial,
            @QueryParam("page")             @DefaultValue("0")   int page,
            @QueryParam("size")             @DefaultValue("100") int size) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        int sizeValido = validarSize(size);
        int pageValido = Math.max(page, 0);
        LOG.debugf("[MOVIM-RES] listar agente=%d fundo=%d sitRms=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial, pageValido, sizeValido);
        return movimentacaoService.listar(cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial, pageValido, sizeValido);
    }

    // =========================================================================
    // Detalhe por nrSequencial — endpoint 18
    // =========================================================================

    @GET
    @Path("/{nrSequencial}/detalhe")
    @Funcionalidade("MOVIMENTACOES_DETALHE")
    @Operation(summary = "Detalhe de movimentação financeira",
            description = "Retorna o breakdown por tipo (DB2GFG.RSM_MVTC_FNCR_RMS) de uma remessa. "
                    + "Path param: NR_SEQL_RMS (número sequencial da remessa). Fonte: query DET_MVT_FNCR do BI.")
    public List<MovimentacaoDetalheDto> detalhe(
            @PathParam("nrSequencial") short nrSequencial) {
        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[MOVIM-RES] detalhe agente=%d nrSeql=%d", cdAgtFnco, nrSequencial);
        return movimentacaoService.buscarDetalhe(cdAgtFnco, nrSequencial);
    }

    // =========================================================================
    // Exportação CSV — endpoint 19
    // =========================================================================

    @GET
    @Path("/exportar")
    @Produces("text/csv")
    @Funcionalidade("MOVIMENTACOES_LISTA")
    @Operation(summary = "Exporta movimentações em CSV",
            description = "Retorna todas as movimentações (sem paginação) em formato CSV para download.")
    public Response exportar(
            @QueryParam("cdFundo")          @DefaultValue("-1") int cdFundo,
            @QueryParam("situacaoRemessa")  @DefaultValue("-1") int situacaoRemessa,
            @QueryParam("nrSequencial")                         Short nrSequencial) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[MOVIM-RES] exportar agente=%d fundo=%d sitRms=%d seq=%s",
                cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial);

        StreamingOutput stream = output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write("FUNDO;NR_SEQUENCIAL;TIPO_MOV_FIN;SITUACAO_MOV;SITUACAO_REMESSA;"
                    + "DT_PROC_REMESSA;DT_ATUAL_MONETARIA;DT_MOV_FINANCEIRA;"
                    + "QT_REGISTROS;VL_LIQ_MOVIMENTADO\n");

            List<MovimentacaoItemDto> items = movimentacaoService.listarTodos(
                    cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial);

            for (MovimentacaoItemDto item : items) {
                writer.write(csvLine(
                        item.nomeFundo(),
                        String.valueOf(item.numeroSequencialRemessa()),
                        item.tipoMovimentacaoFinanceira(),
                        item.situacaoMovFinanceira(),
                        item.situacaoRemessa(),
                        formatDate(item.dataProcessamento()),
                        formatDate(item.dataAtualizacaoMonetaria()),
                        formatDate(item.dataMovimentacaoFinanceira()),
                        formatInt(item.quantidadeRegistros()),
                        formatBD(item.valorLiquidoMovimentado())
                ));
            }
            writer.flush();
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"movimentacoes.csv\"")
                .build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int validarSize(int size) {
        if (size == 10 || size == 50 || size == 100) return size;
        return 100;
    }

    private String csvLine(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(escapeCSV(fields[i]));
        }
        sb.append('\n');
        return sb.toString();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatDate(LocalDate d) {
        return d != null ? d.toString() : "";
    }

    private String formatInt(Integer v) {
        return v != null ? v.toString() : "";
    }

    private String formatBD(BigDecimal bd) {
        return bd != null ? bd.toPlainString() : "";
    }
}
