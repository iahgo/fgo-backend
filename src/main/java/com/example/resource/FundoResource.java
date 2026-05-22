package com.example.resource;

import com.example.dto.painel.FundoDto;
import com.example.service.PainelService;
import jakarta.inject.Inject;
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
 * Resource REST para listagem de fundos garantidores do agente.
 *
 * GET /api/v1/fundos — endpoint 1
 *
 * TODO: substituir @QueryParam("cdAgtFnco") por contexto JWT quando a autenticação for implementada.
 */
@Path("/api/v1/fundos")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Fundos", description = "Listagem de fundos garantidores disponíveis para o agente")
public class FundoResource {

    private static final Logger LOG = Logger.getLogger(FundoResource.class);

    @Inject
    PainelService painelService;

    /**
     * Lista os fundos garantidores nos quais o agente possui operações.
     *
     * @param cdAgtFnco código do agente financeiro (obrigatório)
     *                  TODO: substituir por JWT context
     */
    @GET
    @Operation(summary = "Lista fundos do agente",
            description = "Retorna os fundos garantidores nos quais o agente autenticado possui operações.")
    public List<FundoDto> listar(
            // TODO: substituir por JWT context
            @QueryParam("cdAgtFnco") int cdAgtFnco) {
        LOG.debugf("[FUNDO-RES] listar agente=%d", cdAgtFnco);
        return painelService.listarFundos(cdAgtFnco);
    }
}
