package com.example.resource;

import com.example.dto.ErroDto;
import com.example.dto.OperacaoResumoDto;
import com.example.loader.OperacaoLoader;
import com.example.security.ContextoSeguranca;
import com.example.security.Funcionalidade;
import com.example.service.OperacaoService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/operacoes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operacoes", description = "KPIs de operacoes de credito (cache Redis)")
public class OperacaoResource {

    private static final Logger LOG = Logger.getLogger(OperacaoResource.class);

    @Inject OperacaoService service;
    @Inject ContextoSeguranca contexto;
    @Inject OperacaoLoader loader;
    @Inject ManagedExecutor executor;

    @GET
    @Funcionalidade("OPERACOES_KPI")
    @Operation(summary = "KPIs de operacoes do agente", description = "Retorna KPIs do snapshot mais recente. Servido 100% do Redis.")
    public OperacaoResumoDto getResumo() {
        int codAgente = contexto.getCdAgtFnco();
        LOG.debugf("[RESOURCE] GET /api/operacoes | agente=%d", codAgente);
        return service.getResumo(codAgente);
    }

    @POST
    @Path("/admin/reload")
    @Operation(summary = "Forca recarga do Redis — todos os agentes")
    public Response recarregarTodos() {
        executor.submit(loader::recarregarTudo);
        return Response.accepted(new ErroDto("RELOAD_INICIADO", "Recarga iniciada em background.")).build();
    }

    @POST
    @Path("/admin/reload/{codigoAgente}")
    @Operation(summary = "Forca recarga do Redis — agente especifico")
    public Response recarregarAgente(@PathParam("codigoAgente") int codigoAgente) {
        executor.submit(() -> loader.recarregarAgente(codigoAgente));
        return Response.accepted(new ErroDto("RELOAD_INICIADO", "Recarga do agente " + codigoAgente + " iniciada.")).build();
    }
}
