package com.example.resource;

import com.example.dto.painel.FundoDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.PainelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/fundos")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Fundos", description = "Listagem de fundos garantidores disponíveis para o agente")
public class FundoResource {

    private static final Logger LOG = Logger.getLogger(FundoResource.class);

    @Inject
    PainelService painelService;

    @Inject
    ContextoSeguranca contexto;

    /**
     * Lista os fundos garantidores nos quais o agente possui operações.
     */
    @GET
    @Funcionalidade("FUNDOS_LISTA")
    @Operation(summary = "Lista fundos do agente",
            description = "Retorna os fundos garantidores nos quais o agente autenticado possui operações.")
    public List<FundoDto> listar() {
        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[FUNDO-RES] listar agente=%d", cdAgtFnco);
        return painelService.listarFundos(cdAgtFnco);
    }
}
