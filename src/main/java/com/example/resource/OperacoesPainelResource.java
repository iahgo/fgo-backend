package com.example.resource;

import com.example.dto.PageDto;
import com.example.dto.listagem.OperacaoItemDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.OperacaoListagemService;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Resource REST para listagem e exportação de operações de crédito.
 *
 * GET /api/v1/operacoes          — endpoint 10: listagem paginada
 * GET /api/v1/operacoes/exportar — endpoint 11: exportação CSV
 *
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/operacoes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operações v1", description = "Listagem e exportação de operações de crédito — fonte: DB2D4W.CTRA_FNDO_GRTR (snapshot DT_REF=MAX, query BASE_ANL_OPR do BI)")
public class OperacoesPainelResource {

    private static final Logger LOG = Logger.getLogger(OperacoesPainelResource.class);

    @Inject
    OperacaoListagemService operacaoListagemService;

    @Inject
    ContextoSeguranca contexto;

    // =========================================================================
    // Listagem paginada — endpoint 10
    // =========================================================================

    @GET
    @Funcionalidade("OPERACOES_LISTA")
    @Operation(summary = "Lista operações paginadas",
            description = "Retorna operações de crédito do agente — DB2D4W.CTRA_FNDO_GRTR filtrado por DT_REF=MAX. Inclui Pronampe Solidário RS 1/RS 2. Filtros: fundo, programa, nrContrato (LIKE).")
    public PageDto<OperacaoItemDto> listar(
            @QueryParam("cdFundo")    @DefaultValue("-1")  int cdFundo,
            @QueryParam("cdPrograma") @DefaultValue("-1")  int cdPrograma,
            @QueryParam("nrContrato")                      String nrContrato,
            @QueryParam("page")       @DefaultValue("0")   int page,
            @QueryParam("size")       @DefaultValue("100") int size) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        int sizeValido = validarSize(size);
        int pageValido = Math.max(page, 0);
        LOG.debugf("[OPR-PAINEL] listar agente=%d fundo=%d prog=%d cont=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato, pageValido, sizeValido);
        return operacaoListagemService.listar(cdAgtFnco, cdFundo, cdPrograma, nrContrato, pageValido, sizeValido);
    }

    // =========================================================================
    // Exportação CSV — endpoint 11
    // =========================================================================

    @GET
    @Path("/exportar")
    @Produces("text/csv")
    @Funcionalidade("OPERACOES_LISTA")
    @Operation(summary = "Exporta operações em CSV",
            description = "Retorna todas as operações (sem paginação) em formato CSV para download.")
    public Response exportar(
            @QueryParam("cdFundo")    @DefaultValue("-1")  int cdFundo,
            @QueryParam("cdPrograma") @DefaultValue("-1")  int cdPrograma,
            @QueryParam("nrContrato")                      String nrContrato) {

        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[OPR-PAINEL] exportar agente=%d fundo=%d prog=%d cont=%s",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato);

        StreamingOutput stream = output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write("FUNDO;PROGRAMA;NR_OPERACAO;PUBLICO_ALVO;ESTADO_OPERACAO;"
                    + "DATA_FORMALIZACAO;DATA_VENCIMENTO;VALOR_OPERACAO;VALOR_LIBERADO\n");

            List<OperacaoItemDto> items = operacaoListagemService.listarTodos(
                    cdAgtFnco, cdFundo, cdPrograma, nrContrato);

            for (OperacaoItemDto item : items) {
                writer.write(csvLine(
                        item.nomeFundo(),
                        item.nomePrograma(),
                        item.numeroOperacao(),
                        item.publicoAlvo(),
                        item.estadoOperacao(),
                        formatDate(item.dataFormalizacao()),
                        formatDate(item.dataVencimento()),
                        formatBD(item.valorOperacao()),
                        formatBD(item.valorLiberado())
                ));
            }
            writer.flush();
        };

        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"operacoes.csv\"")
                .build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Valida e normaliza o parâmetro size: aceita apenas 10, 50 ou 100.
     * Qualquer outro valor retorna 100 (default).
     */
    private int validarSize(int size) {
        if (size == 10 || size == 50 || size == 100) return size;
        LOG.debugf("[OPR-PAINEL] size=%d inválido — usando 100", size);
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

    private String formatDate(LocalDate date) {
        return date != null ? date.toString() : "";
    }

    private String formatBD(BigDecimal bd) {
        return bd != null ? bd.toPlainString() : "";
    }
}
