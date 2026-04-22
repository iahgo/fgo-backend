package com.example.resource;

import com.example.dto.SistemaDto;
import com.example.service.SistemaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Endpoint de monitoramento — métricas completas do sistema.
 *
 * Somente leitura, sem efeitos colaterais. Seguro de chamar a qualquer momento.
 */
@Path("/admin/sistema")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin — Monitoramento", description = "Métricas do sistema (servidor, JVM, Redis, DB2)")
public class SistemaResource {

    @Inject
    SistemaService service;

    @GET
    @Operation(
        summary = "Métricas completas do sistema",
        description = """
            Retorna em um único JSON:

            **Servidor (OS)**
            - Hostname, OS, arquitetura, número de CPUs
            - Load average do último minuto
            - Memória física total/livre/usada (GB e %)
            - Uso de disco por ponto de montagem (GB e %)

            **JVM**
            - Versão do Java, heap usado/máximo (MB e %), non-heap
            - Threads ativos e total de threads criados
            - Uptime do processo em ms

            **Redis**
            - Versão, modo (standalone/sentinel/cluster), uptime do servidor
            - Memória usada e pico (MB)
            - Total de chaves, clientes conectados
            - Total de comandos processados
            - Cache hits/misses e hit ratio (%)

            **DB2 — SYSCAT.TABLES**
            - Todas as tabelas do schema DB2GFG
            - Estimativa de linhas (CARD), páginas alocadas (FPAGES)
            - Tamanho calculado em MB e GB (FPAGES × PAGESIZE)

            Operação somente leitura — sem nenhum efeito colateral.
            """
    )
    @APIResponse(responseCode = "200", description = "Métricas coletadas com sucesso")
    public SistemaDto getSistema() {
        return service.coletar();
    }
}
