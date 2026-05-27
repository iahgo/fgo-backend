package com.example.resource;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.PendenciaItemDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.PendenciaService;
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
import java.util.List;

/**
 * Resource REST para listagem, filtros e exportação de pendências.
 *
 * GET /api/v1/pendencias/filtros/tipos — endpoint 20: filtros de tipo de pendência
 * GET /api/v1/pendencias               — endpoint 21: listagem paginada
 * GET /api/v1/pendencias/exportar      — endpoint 22: exportação CSV
 *
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/pendencias")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Pendências v1", description = "Listagem, filtros e exportação de pendências — fonte: DB2D4W.DETT_OPR_PND (query PENDENCIAS do BI, custo 48472)")
public class PendenciaResource {

    private static final Logger LOG = Logger.getLogger(PendenciaResource.class);

    @Inject
    PendenciaService pendenciaService;

    @Inject
    ContextoSeguranca contexto;

    // =========================================================================
    // Filtros de tipo de pendência — endpoint 20
    // =========================================================================

    @GET
    @Path("/filtros/tipos")
    @Operation(summary = "Lista tipos de pendência para filtro",
            description = "Retorna os códigos e labels dos tipos de pendência disponíveis como filtro.")
    public List<FiltroItemDto> filtrosTipos() {
        return pendenciaService.listarTiposPendencia();
    }

    // =========================================================================
    // Listagem paginada — endpoint 21
    // =========================================================================

    @GET
    @Funcionalidade("PENDENCIAS_LISTA")
    @Operation(summary = "Lista pendências paginadas",
            description = "Retorna pendências da tabela DB2D4W.DETT_OPR_PND. Campo NM_TIP_PNC_OPR_CRD identifica o tipo (texto). Filtro tipoPendencia filtra por texto exato desse campo. Data via DT_SNC_PHC.")
    public PageDto<PendenciaItemDto> listar(
            @QueryParam("cdFundo")      @DefaultValue("-1")  int cdFundo,
            @QueryParam("cdPrograma")   @DefaultValue("-1")  int cdPrograma,
            @QueryParam("tipoPendencia")                     String tipoPendencia,
            @QueryParam("page")         @DefaultValue("0")   int page,
            @QueryParam("size")         @DefaultValue("100") int size) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        int sizeValido = validarSize(size);
        int pageValido = Math.max(page, 0);
        LOG.debugf("[PEND-RES] listar agente=%d fundo=%d prog=%d tipo=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, pageValido, sizeValido);
        return pendenciaService.listar(cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, pageValido, sizeValido);
    }

    // =========================================================================
    // Exportação CSV — endpoint 22
    // =========================================================================

    @GET
    @Path("/exportar")
    @Produces("text/csv")
    @Funcionalidade("PENDENCIAS_LISTA")
    @Operation(summary = "Exporta pendências em CSV",
            description = "Retorna todas as pendências (sem paginação) em formato CSV para download.")
    public Response exportar(
            @QueryParam("cdFundo")      @DefaultValue("-1") int cdFundo,
            @QueryParam("cdPrograma")   @DefaultValue("-1") int cdPrograma,
            @QueryParam("tipoPendencia")                    String tipoPendencia) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[PEND-RES] exportar agente=%d fundo=%d prog=%d tipo=%s",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);

        StreamingOutput stream = output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write("FUNDO;PROGRAMA;NR_CONTRATO;SITUACAO_CONTRATO;TIPO_PENDENCIA;DATA_INICIO_PENDENCIA\n");

            List<PendenciaItemDto> items = pendenciaService.listarTodos(
                    cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);

            for (PendenciaItemDto item : items) {
                writer.write(csvLine(
                        item.nomeFundo(),
                        item.nomePrograma(),
                        item.numeroContrato(),
                        item.situacaoContrato(),
                        item.tipoPendencia(),
                        formatDate(item.dataInicioPendencia())
                ));
            }
            writer.flush();
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"pendencias.csv\"")
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
}
