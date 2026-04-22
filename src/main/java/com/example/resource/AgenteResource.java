package com.example.resource;

import com.example.dto.AgenteDto;
import com.example.service.AgenteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Endpoint REST para listagem de agentes financeiros cadastrados no FGO.
 *
 * Utilizado pelo Angular para popular selects e validar o cod_agente
 * antes de enviar a requisição ao MS Operação.
 */
@Path("/api/agentes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agentes", description = "Listagem dos agentes financeiros habilitados no FGO")
public class AgenteResource {

    @Inject
    AgenteService service;

    @GET
    @Operation(
        summary = "Lista todos os agentes financeiros",
        description = """
            Retorna os agentes financeiros cadastrados no FGO (tabela AGT_FNCO).
            Em produção, o IIB usa esta lista para validar o cod_agente do token.
            """
    )
    @APIResponse(responseCode = "200", description = "Lista de agentes retornada com sucesso")
    public List<AgenteDto> listarTodos() {
        return service.listarTodos();
    }
}
