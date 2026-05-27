package com.example.resource;

import com.example.dto.painel.ProgramaDto;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
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
 * Resource REST para listagem de programas de crédito do agente.
 *
 * GET /api/v1/programas — endpoint 2
 *
 * O cdAgtFnco é extraído do contexto JWT (ContextoSeguranca) — não é passado como parâmetro.
 */
@Path("/api/v1/programas")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Programas", description = "Listagem de programas de crédito disponíveis para o agente")
public class ProgramaResource {

    private static final Logger LOG = Logger.getLogger(ProgramaResource.class);

    @Inject
    PainelService painelService;

    @Inject
    ContextoSeguranca contexto;

    /**
     * Lista os programas de crédito nos quais o agente possui operações.
     *
     * @param cdFundo   filtro opcional por fundo (-1 = todos os fundos)
     */
    @GET
    @Funcionalidade("PROGRAMAS_LISTA")
    @Operation(summary = "Lista programas do agente",
            description = "Retorna os programas de crédito nos quais o agente possui operações, "
                    + "opcionalmente filtrado por fundo garantidor.")
    public List<ProgramaDto> listar(
            @QueryParam("cdFundo")   @DefaultValue("-1") int cdFundo) {
        int cdAgtFnco = contexto.getCdAgtFnco();
        LOG.debugf("[PROGRAMA-RES] listar agente=%d fundo=%d", cdAgtFnco, cdFundo);
        return painelService.listarProgramas(cdAgtFnco, cdFundo);
    }
}
