package com.example.resource;

import com.example.log.LogBuffer;
import com.example.log.LogBuffer.LogEntry;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * Endpoint de logs em memória do pod.
 *
 * Captura as últimas N linhas de log deste pod específico.
 * Cada pod tem seu próprio buffer — em ambientes multi-pod use oc logs para ver todos.
 */
@Path("/admin/logs")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin — Logs", description = "Logs em memória deste pod (últimas 2.000 linhas)")
public class LogResource {

    @Inject
    LogBuffer buffer;

    @GET
    @Operation(
        summary = "Logs em memória deste pod",
        description = """
            Retorna as últimas linhas de log capturadas **neste pod**.

            Inclui todos os níveis: INFO, WARN, ERROR, DEBUG.
            Inclui logs de acesso do filtro `[REQ]`/`[RES]` com elapsed time.

            **Importante:** Em ambientes com múltiplos pods (replicas=3), cada pod
            tem seu próprio buffer. Use `oc logs <pod>` para ver logs completos
            ou `GET /admin/logs?nivel=ERROR` para filtrar por nível.

            Somente leitura — sem efeitos colaterais.
            """
    )
    @APIResponse(responseCode = "200", description = "Logs retornados com sucesso")
    public Map<String, Object> getLogs(
            @Parameter(description = "Máximo de linhas retornadas (1–2000, padrão 200)")
            @QueryParam("limite") @DefaultValue("200") int limite,

            @Parameter(description = "Filtro por nível: INFO, WARN, ERROR, DEBUG (opcional)")
            @QueryParam("nivel") String nivel) {

        int limiteSeguro = Math.min(Math.max(limite, 1), 2000);

        List<LogEntry> entries = buffer.getLast(limiteSeguro);

        if (nivel != null && !nivel.isBlank()) {
            String nivelUp = nivel.toUpperCase();
            entries = entries.stream()
                    .filter(e -> e.nivel().equalsIgnoreCase(nivelUp)
                              || e.nivel().startsWith(nivelUp))
                    .toList();
        }

        return Map.of(
                "totalNoBuffer", buffer.size(),
                "retornados",    entries.size(),
                "filtroNivel",   nivel != null ? nivel.toUpperCase() : "TODOS",
                "logs",          entries
        );
    }
}
