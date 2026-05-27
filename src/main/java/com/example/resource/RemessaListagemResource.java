package com.example.resource;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.RemessaItemDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.RemessaListagemService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resource REST para listagem, filtros e exportação de remessas do agente.
 *
 * GET /api/v1/remessas/filtros/situacoes        — endpoint 12: filtros de situação
 * GET /api/v1/remessas/filtros/motivos-rejeicao — endpoint 13: filtros de motivo de rejeição
 * GET /api/v1/remessas                          — endpoint 14: listagem paginada
 * GET /api/v1/remessas/exportar                 — endpoint 15: exportação CSV
 *
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/remessas")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Remessas v1", description = "Listagem, filtros e exportação de remessas — fonte: DB2GFG.RMS_AGT_FNCO (query REMESSAS do BI, custo 2223). Inclui QT_REG_ACT calculado via EVT_OPR_R3TD.")
public class RemessaListagemResource {

    private static final Logger LOG = Logger.getLogger(RemessaListagemResource.class);

    @Inject
    RemessaListagemService remessaListagemService;

    @Inject
    ContextoSeguranca contexto;

    // =========================================================================
    // Filtros de situação — endpoint 12
    // =========================================================================

    @GET
    @Path("/filtros/situacoes")
    @Operation(summary = "Lista situações de remessa para filtro",
            description = "Retorna os códigos e labels das situações de remessa disponíveis como filtro.")
    public List<FiltroItemDto> filtrosSituacoes() {
        return remessaListagemService.listarSituacoes();
    }

    // =========================================================================
    // Filtros de motivos de rejeição — endpoint 13
    // =========================================================================

    @GET
    @Path("/filtros/motivos-rejeicao")
    @Operation(summary = "Lista motivos de rejeição para filtro",
            description = "Retorna os códigos e labels dos motivos de rejeição disponíveis como filtro.")
    public List<FiltroItemDto> filtrosMotivosRejeicao() {
        return remessaListagemService.listarMotivosRejeicao();
    }

    // =========================================================================
    // Listagem paginada — endpoint 14
    // =========================================================================

    @GET
    @Funcionalidade("REMESSAS_LISTA")
    @Operation(summary = "Lista remessas paginadas",
            description = "Retorna remessas do agente com filtros opcionais, paginado.")
    public PageDto<RemessaItemDto> listar(
            @QueryParam("cdFundo")         @DefaultValue("-1")  int cdFundo,
            @QueryParam("situacao")        @DefaultValue("-1")  int situacao,
            @QueryParam("motivoRejeicao")  @DefaultValue("-1")  int motivoRejeicao,
            @QueryParam("nrSequencial")                         Short nrSequencial,
            @QueryParam("page")            @DefaultValue("0")   int page,
            @QueryParam("size")            @DefaultValue("100") int size) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        int sizeValido = validarSize(size);
        int pageValido = Math.max(page, 0);
        LOG.debugf("[REMESSA-RES] listar agente=%d fundo=%d sit=%d motivo=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial, pageValido, sizeValido);
        return remessaListagemService.listar(cdAgtFnco, cdFundo, situacao, motivoRejeicao,
                nrSequencial, pageValido, sizeValido);
    }

    // =========================================================================
    // Exportação CSV — endpoint 15
    // =========================================================================

    @GET
    @Path("/exportar")
    @Produces("text/csv")
    @Funcionalidade("REMESSAS_LISTA")
    @Operation(summary = "Exporta remessas em CSV",
            description = "Retorna todas as remessas (sem paginação) em formato CSV para download.")
    public Response exportar(
            @QueryParam("cdFundo")         @DefaultValue("-1")  int cdFundo,
            @QueryParam("situacao")        @DefaultValue("-1")  int situacao,
            @QueryParam("motivoRejeicao")  @DefaultValue("-1")  int motivoRejeicao,
            @QueryParam("nrSequencial")                         Short nrSequencial) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[REMESSA-RES] exportar agente=%d fundo=%d sit=%d motivo=%d seq=%s",
                cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial);

        StreamingOutput stream = output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write("REFERENCIA;DATA_HORA_RECEBIMENTO;NR_SEQUENCIAL;AGENTE;FUNDO;"
                    + "SITUACAO;MOTIVO_REJEICAO;QT_REGISTROS;REGISTROS_ACEITOS;REGISTROS_RECUSADOS\n");

            List<RemessaItemDto> items = remessaListagemService.listarTodos(
                    cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial);

            for (RemessaItemDto item : items) {
                writer.write(csvLine(
                        item.referencia(),
                        formatDT(item.dataHoraRecebimento()),
                        String.valueOf(item.numeroSequencial()),
                        item.agenteFinanceiro(),
                        item.nomeFundo(),
                        item.situacao(),
                        item.motivoRejeicao(),
                        formatInt(item.quantidadeRegistros()),
                        formatInt(item.quantidadeAceitos()),
                        formatInt(item.quantidadeRecusados())
                ));
            }
            writer.flush();
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"remessas.csv\"")
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

    private String formatDT(LocalDateTime dt) {
        return dt != null ? dt.toString() : "";
    }

    private String formatInt(Integer v) {
        return v != null ? v.toString() : "";
    }

    private String formatDate(LocalDate d) {
        return d != null ? d.toString() : "";
    }
}
