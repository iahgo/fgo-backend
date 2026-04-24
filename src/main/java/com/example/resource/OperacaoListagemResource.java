package com.example.resource;

import com.example.domain.OperacaoCreditoFundoGarantidor;
import com.example.dto.OperacaoDetalheDto;
import com.example.repository.OperacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Listagem paginada de operações individuais do DB2.
 *
 * GET /api/operacoes/{mesAno}          → KPIs agregados do Redis (< 1ms) — já existe
 * GET /api/operacoes/lista             → operações individuais paginadas (DB2 direto)
 * GET /api/operacoes/lista/{mesAno}    → operações de um mês específico paginadas
 */
@Path("/api/operacoes/lista")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Tag(
    name = "Operações — Listagem",
    description = "Listagem paginada de operações individuais direto do DB2. " +
                  "⚠️ Endpoints lentos — varrem a tabela de 100M registros. " +
                  "Use os KPIs agregados em `/api/operacoes/{mesAno}` para consultas rápidas."
)
public class OperacaoListagemResource {

    @Inject
    OperacaoRepository repository;

    // =========================================================================
    // GET /api/operacoes/lista — todas as operações do agente, paginado
    // =========================================================================

    @GET
    @Operation(
        summary = "Lista operações individuais do agente (paginado)",
        description = "Retorna operações da tabela OPR_CRD_FNDO_GRTR ordenadas por data decrescente. " +
                      "⚠️ Query no DB2 com 100M registros — use `page` e `size` pequenos."
    )
    public Map<String, Object> listar(
            @Parameter(description = "Código do agente", required = true)
            @HeaderParam("X-Cod-Agente") int codAgente,

            @Parameter(description = "Página (começa em 0)")
            @QueryParam("page") @DefaultValue("0") int page,

            @Parameter(description = "Registros por página (máx 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        int sizeSeguro = Math.min(Math.max(size, 1), 100);
        int pageSeguro = Math.max(page, 0);

        List<OperacaoCreditoFundoGarantidor> ops =
                repository.listarPorAgente(codAgente, pageSeguro, sizeSeguro);
        long total = repository.contarPorAgente(codAgente);

        return paginar(ops, total, pageSeguro, sizeSeguro);
    }

    // =========================================================================
    // GET /api/operacoes/lista/{mesAno} — operações de um mês, paginado
    // =========================================================================

    @GET
    @Path("/{mesAno}")
    @Operation(
        summary = "Lista operações de um mês específico (paginado)",
        description = "Filtra por agente e mês (ex: `2026-04`). " +
                      "⚠️ Query no DB2 com 100M registros — pode demorar alguns segundos."
    )
    public Map<String, Object> listarPorMes(
            @Parameter(description = "Código do agente", required = true)
            @HeaderParam("X-Cod-Agente") int codAgente,

            @Parameter(description = "Mês de referência (formato: YYYY-MM)", required = true)
            @PathParam("mesAno") String mesAno,

            @Parameter(description = "Página (começa em 0)")
            @QueryParam("page") @DefaultValue("0") int page,

            @Parameter(description = "Registros por página (máx 100)")
            @QueryParam("size") @DefaultValue("20") int size) {

        String[] partes = mesAno.split("-");
        if (partes.length != 2) throw new BadRequestException("mesAno deve ser YYYY-MM (ex: 2026-04)");

        int ano = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        int sizeSeguro = Math.min(Math.max(size, 1), 100);
        int pageSeguro = Math.max(page, 0);

        List<OperacaoCreditoFundoGarantidor> ops =
                repository.listarPorAgenteMes(codAgente, ano, mes, pageSeguro, sizeSeguro);
        long total = repository.contarPorAgenteMes(codAgente, ano, mes);

        return paginar(ops, total, pageSeguro, sizeSeguro);
    }

    // =========================================================================

    private Map<String, Object> paginar(List<OperacaoCreditoFundoGarantidor> ops,
                                        long total, int page, int size) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("total", total);
        resultado.put("pagina", page);
        resultado.put("tamanhoPagina", size);
        resultado.put("totalPaginas", (long) Math.ceil((double) total / size));
        resultado.put("operacoes", ops.stream().map(this::toDto).toList());
        return resultado;
    }

    private OperacaoDetalheDto toDto(OperacaoCreditoFundoGarantidor o) {
        OperacaoDetalheDto dto = new OperacaoDetalheDto();
        dto.setId(o.getCdOprCrdFndo());
        dto.setCodAgente(o.getCdAgtFnco());
        dto.setCodFundo(o.getCdFndoGrtr());
        dto.setIdExterno(o.getCdIdfrExnoOpr());
        dto.setPrograma(String.valueOf(o.getCdTipPgmCrd()));
        dto.setTipoPessoa(o.getCdTipPss());
        dto.setCpfCnpj(o.getCdIdfrSrf());
        dto.setVlrOperacao(o.getVlOprCrd());
        dto.setVlrCarteira(o.getVlSdoCptlNmld());
        dto.setVlrAtraso(o.getVlSdoCptlAtr());
        dto.setVlrGarantia(o.getVlGrtOprAjsd());
        dto.setDataFormalizacao(o.getDtFrmzOpr());
        dto.setDataVencimento(o.getDtVnctOpr());
        dto.setEstado(o.getCdTipEstOpr());
        return dto;
    }
}
