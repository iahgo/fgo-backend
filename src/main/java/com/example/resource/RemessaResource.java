package com.example.resource;

import com.example.dto.ErroDto;
import com.example.dto.RemessaDto;
import com.example.service.RemessaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Endpoint REST para consulta de remessas do agente financeiro.
 *
 * Uma remessa é o envio de um lote de operações pelo agente ao fundo garantidor.
 * O agente envia arquivos periódicos (diários/mensais) com novos contratos ou atualizações.
 *
 * Segurança: mesmo modelo do OperacaoResource — cod_agente vem do header X-Cod-Agente
 * injetado pelo IIB. O agente só vê suas próprias remessas.
 */
@Path("/api/remessas")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Remessas", description = "Consulta de lotes de operações enviados pelo agente ao FGO")
public class RemessaResource {

    private static final Logger LOG = Logger.getLogger(RemessaResource.class);

    @Inject
    RemessaService service;

    // =====================================================================
    // GET /api/remessas — lista remessas do agente
    // =====================================================================

    @GET
    @Operation(
        summary = "Lista remessas do agente",
        description = """
            Retorna as remessas mais recentes do agente financeiro (últimas 50 por padrão).
            Cada remessa representa um lote de operações enviado ao FGO.
            O cod_agente é resolvido pelo header X-Cod-Agente injetado pelo IIB.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de remessas retornada com sucesso"),
        @APIResponse(responseCode = "400", description = "Header X-Cod-Agente ausente",
            content = @Content(schema = @Schema(implementation = ErroDto.class)))
    })
    public List<RemessaDto> listar(
            @Parameter(description = "Código interno do agente (injetado pelo IIB em produção)", required = true)
            @HeaderParam("X-Cod-Agente") Integer codAgente,

            @Parameter(description = "Máximo de registros retornados (1–200, padrão 50)")
            @QueryParam("limite") @DefaultValue("50") int limite) {

        if (codAgente == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErroDto("HEADER_AUSENTE", "Header X-Cod-Agente obrigatório."))
                        .build()
            );
        }

        int limiteSeguro = Math.min(Math.max(limite, 1), 200);
        LOG.debugf("[REMESSA] GET /api/remessas | agente=%d | limite=%d", codAgente, limiteSeguro);
        return service.listarPorAgente(codAgente, limiteSeguro);
    }

    // =====================================================================
    // GET /api/remessas/{id} — detalhe de uma remessa
    // =====================================================================

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Detalhe de uma remessa",
        description = """
            Retorna os detalhes de uma remessa específica do agente.
            Valida que a remessa pertence ao agente autenticado (X-Cod-Agente).
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Remessa encontrada"),
        @APIResponse(responseCode = "400", description = "Header X-Cod-Agente ausente"),
        @APIResponse(responseCode = "404", description = "Remessa não encontrada ou não pertence ao agente",
            content = @Content(schema = @Schema(implementation = ErroDto.class)))
    })
    public RemessaDto buscarPorId(
            @PathParam("id") int id,
            @HeaderParam("X-Cod-Agente") Integer codAgente) {

        if (codAgente == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErroDto("HEADER_AUSENTE", "Header X-Cod-Agente obrigatório."))
                        .build()
            );
        }

        LOG.debugf("[REMESSA] GET /api/remessas/%d | agente=%d", id, codAgente);
        return service.buscarPorId(id, codAgente)
                .orElseThrow(() -> new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(new ErroDto("REMESSA_NAO_ENCONTRADA",
                                    "Remessa id=" + id + " não encontrada para agente=" + codAgente))
                            .build()
                ));
    }
}
