package com.example.resource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import com.example.service.SeedIndividualService;
import com.example.service.SeedService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint administrativo para geração de dados de teste em massa no DB2.
 *
 * POST /admin/seed?quantidade=35000000&limpar=true  → 202 Accepted (execução assíncrona)
 * POST /admin/seed/remessas                         → 202 Accepted (só remessas, não apaga operações)
 * GET  /admin/seed/status                           → JSON com progresso atual
 */
@Path("/admin/seed")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Tag(
    name = "Admin — Seed de Dados",
    description = """
        **ATENÇÃO — ENDPOINTS DESTRUTIVOS**

        Os endpoints abaixo operam sobre as tabelas reais do DB2.
        Leia a descrição de cada um antes de executar.

        - `POST /admin/seed` — **APAGA TUDO** e recria com dados sintéticos.
          Perda total dos 100M de registros de operações. Levará algumas horas para inserir os 100 milhões de registros.
        - `POST /admin/seed/remessas` — Apaga e recria APENAS a tabela de remessas.
          Operações (OPR_CRD_FNDO_GRTR) são preservadas.
        - `GET /admin/seed/status` — Somente leitura. Seguro.
        """
)
public class SeedResource {

    private static final Logger LOG = Logger.getLogger(SeedResource.class);

    @Inject
    SeedService seedService;

    @Inject
    SeedIndividualService individual;

    @Inject
    ManagedExecutor executor;

    // =========================================================================
    // POST /admin/seed — FULL SEED (DESTRUTIVO)
    // =========================================================================

    @POST
    @Operation(
        summary = "DESTRUTIVO — Apaga tudo e regera dados sintéticos",
        description = """
            ⚠️ **OPERAÇÃO IRREVERSÍVEL — NÃO EXECUTE EM PRODUÇÃO**

            Com `limpar=true` (padrão):
            - **TRUNCATE** em `OPR_CRD_FNDO_GRTR` (100M+ registros — perda total)
            - **DELETE** em todas as tabelas de domínio (agentes, fundos, programas…)
            - Reinserção dos dados mestres
            - Geração de `quantidade` novas operações sintéticas

            Com `limpar=false`:
            - Apenas insere novas operações sem apagar as existentes.

            A execução ocorre em background — o endpoint retorna 202 imediatamente.
            Acompanhe via `GET /admin/seed/status`.

            **Impacto:** O warm-up do Redis será necessário após a conclusão
            (`POST /api/operacoes/admin/reload`).
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Geração iniciada em background"),
        @APIResponse(responseCode = "400", description = "Quantidade fora do intervalo permitido"),
        @APIResponse(responseCode = "409", description = "Outra geração já está em andamento")
    })
    public Response iniciar(
            @Parameter(description = "Número de operações a gerar (1 a 500.000.000)")
            @QueryParam("quantidade") @DefaultValue("1000000") long quantidade,

            @Parameter(description = "**true** = apaga TODOS os dados antes (IRREVERSÍVEL). false = apenas insere.")
            @QueryParam("limpar") @DefaultValue("true") boolean limpar) {

        if (seedService.isEmExecucao()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "erro",      "Geração já em andamento",
                            "progresso", seedService.getProgresso(),
                            "total",     seedService.getTotal(),
                            "status",    seedService.getStatusMsg()
                    ))
                    .build();
        }

        if (quantidade <= 0 || quantidade > 500_000_000L) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "quantidade deve estar entre 1 e 500.000.000"))
                    .build();
        }

        LOG.infof("[SEED] Iniciando geração: quantidade=%d limpar=%b", quantidade, limpar);

        final long qtd    = quantidade;
        final boolean lmp = limpar;
        executor.submit(() -> {
            try {
                seedService.gerarDados(qtd, lmp);
            } catch (Exception e) {
                LOG.errorf(e, "[SEED] Falha durante geração de dados");
            }
        });

        return Response.accepted(Map.of(
                "mensagem",   "Geração iniciada em background",
                "quantidade", qtd,
                "limpar",     lmp,
                "aviso",      limpar ? "TODOS os dados existentes serão apagados!" : "Inserção sem limpeza",
                "statusUrl",  "/admin/seed/status"
        )).build();
    }

    // =========================================================================
    // POST /admin/seed/remessas — SEED DE REMESSAS (preserva operações)
    // =========================================================================

    @POST
    @Path("/remessas")
    @Operation(
        summary = "TRUNCATE remessas — popula RMS_AGT_FNCO (preserva operações)",
        description = """
            ⚠️ **Apaga e recria a tabela de remessas (RMS_AGT_FNCO)**

            - Faz **TRUNCATE** apenas em `RMS_AGT_FNCO`
            - **Preserva** os 100M de registros de `OPR_CRD_FNDO_GRTR`
            - Insere 1 remessa por agente × fundo × dia de 2024-01-01 até hoje
            - Total esperado: ~11.800 registros

            A execução ocorre em background — o endpoint retorna 202 imediatamente.
            Acompanhe via `GET /admin/seed/status`.
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Geração de remessas iniciada em background"),
        @APIResponse(responseCode = "409", description = "Outra geração já está em andamento")
    })
    public Response gerarRemessas() {
        if (seedService.isEmExecucao()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("erro", "Geração já em andamento", "status", seedService.getStatusMsg()))
                    .build();
        }

        LOG.info("[SEED] Iniciando seed de remessas (sem apagar operações).");
        executor.submit(() -> {
            try {
                seedService.gerarSomenteRemessas();
            } catch (Exception e) {
                LOG.errorf(e, "[SEED] Falha ao gerar remessas");
            }
        });

        return Response.accepted(Map.of(
                "mensagem",  "Geração de remessas iniciada em background (~11.800 registros)",
                "aviso",     "Apenas RMS_AGT_FNCO será truncada. OPR_CRD_FNDO_GRTR preservada.",
                "statusUrl", "/admin/seed/status"
        )).build();
    }

    // =========================================================================
    // GET /admin/seed/status — somente leitura
    // =========================================================================

    @GET
    @Path("/status")
    @Operation(
        summary = "Progresso da geração de dados",
        description = "Somente leitura. Retorna o status atual da geração em background."
    )
    @APIResponse(responseCode = "200", description = "Status retornado")
    public Response status() {
        long prog  = seedService.getProgresso();
        long total = seedService.getTotal();
        double pct = total > 0 ? (prog * 100.0 / total) : 0.0;

        return Response.ok(Map.of(
                "emExecucao", seedService.isEmExecucao(),
                "progresso",  prog,
                "total",      total,
                "percentual", String.format("%.2f%%", pct),
                "status",     seedService.getStatusMsg()
        )).build();
    }

    // =========================================================================
    // INSERTS INDIVIDUAIS — inserção unitária por tabela
    // =========================================================================

    @POST
    @Path("/agente")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Insere um agente financeiro",
        description = """
            Insere uma linha em `AGT_FNCO` e cria a associação em `AGT_FNCO_FNDO_GRTR`
            para cada fundo já existente no banco.

            **Campos obrigatórios:** `codAgente`, `nome`

            **Campos opcionais:**
            - `ispb` — ISPB de 8 dígitos (padrão `"00000000"`)
            - `codCli` — código do cliente interno (padrão `1000 + codAgente`)

            **Exemplo de body:**
            ```json
            { "codAgente": 8, "nome": "BANCO XYZ SA", "ispb": "30306294" }
            ```
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Agente inserido"),
        @APIResponse(responseCode = "400", description = "Campos obrigatórios ausentes"),
        @APIResponse(responseCode = "500", description = "Erro no DB2 (ex: chave duplicada)")
    })
    public Response inserirAgente(Map<String, Object> body) {
        Object codObj  = body.get("codAgente");
        Object nomeObj = body.get("nome");
        if (codObj == null || nomeObj == null) {
            return Response.status(400).entity(Map.of("erro", "codAgente e nome são obrigatórios")).build();
        }
        int    codAgente = ((Number) codObj).intValue();
        String nome      = nomeObj.toString();
        String ispb      = body.getOrDefault("ispb",   "").toString();
        Object codCliObj = body.get("codCli");
        Integer codCli   = codCliObj != null ? ((Number) codCliObj).intValue() : null;
        try {
            individual.inserirAgente(codAgente, nome, ispb, codCli);
            return Response.status(201).entity(Map.of(
                    "mensagem",   "Agente inserido com sucesso",
                    "codAgente",  codAgente,
                    "nome",       nome
            )).build();
        } catch (Exception e) {
            LOG.errorf(e, "[SEED-IND] Falha ao inserir agente %d", codAgente);
            return Response.serverError().entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("/fundo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Insere um fundo garantidor",
        description = """
            Insere uma linha em `FNDO_GRTR` e cria a associação em `AGT_FNCO_FNDO_GRTR`
            para cada agente já existente no banco.

            **Campos obrigatórios:** `codFundo`, `sigla`, `nome`

            **Exemplo de body:**
            ```json
            { "codFundo": 3, "sigla": "FGR", "nome": "Fundo de Garantia Rural" }
            ```
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Fundo inserido"),
        @APIResponse(responseCode = "400", description = "Campos obrigatórios ausentes"),
        @APIResponse(responseCode = "500", description = "Erro no DB2")
    })
    public Response inserirFundo(Map<String, Object> body) {
        Object codObj   = body.get("codFundo");
        Object siglaObj = body.get("sigla");
        Object nomeObj  = body.get("nome");
        if (codObj == null || siglaObj == null || nomeObj == null) {
            return Response.status(400).entity(Map.of("erro", "codFundo, sigla e nome são obrigatórios")).build();
        }
        int    codFundo = ((Number) codObj).intValue();
        String sigla    = siglaObj.toString();
        String nome     = nomeObj.toString();
        try {
            individual.inserirFundo(codFundo, sigla, nome);
            return Response.status(201).entity(Map.of(
                    "mensagem",  "Fundo inserido com sucesso",
                    "codFundo",  codFundo,
                    "sigla",     sigla
            )).build();
        } catch (Exception e) {
            LOG.errorf(e, "[SEED-IND] Falha ao inserir fundo %d", codFundo);
            return Response.serverError().entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("/programa")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Insere um programa de crédito",
        description = """
            Insere uma linha em `TIP_PGM_CRD`.

            **Campos obrigatórios:** `codPrograma`, `nome`

            **Exemplo de body:**
            ```json
            { "codPrograma": 7, "nome": "Novo Programa Emergencial" }
            ```
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Programa inserido"),
        @APIResponse(responseCode = "400", description = "Campos obrigatórios ausentes"),
        @APIResponse(responseCode = "500", description = "Erro no DB2")
    })
    public Response inserirPrograma(Map<String, Object> body) {
        Object codObj  = body.get("codPrograma");
        Object nomeObj = body.get("nome");
        if (codObj == null || nomeObj == null) {
            return Response.status(400).entity(Map.of("erro", "codPrograma e nome são obrigatórios")).build();
        }
        int    codPrograma = ((Number) codObj).intValue();
        String nome        = nomeObj.toString();
        try {
            individual.inserirPrograma(codPrograma, nome);
            return Response.status(201).entity(Map.of(
                    "mensagem",    "Programa inserido com sucesso",
                    "codPrograma", codPrograma,
                    "nome",        nome
            )).build();
        } catch (Exception e) {
            LOG.errorf(e, "[SEED-IND] Falha ao inserir programa %d", codPrograma);
            return Response.serverError().entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("/operacao")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Insere uma operação de crédito individual",
        description = """
            Insere uma linha em `OPR_CRD_FNDO_GRTR`. O ID é gerado automaticamente
            (MAX + 1). Campos não informados recebem valores sintéticos aleatórios.

            **Campos obrigatórios:** `codAgente`

            **Campos opcionais:**
            - `codFundo` — padrão `1`
            - `codPrograma` — padrão aleatório (1-6)
            - `vlrOperacao` — valor em R$ (padrão aleatório 10k-2M)
            - `dataFormalizacao` — formato `YYYY-MM-DD` (padrão hoje)
            - `tipoPessoa` — 1=PF, 2=PJ, 3=Rural (padrão 1)
            - `cpfCnpj` — número sem formatação (padrão aleatório)

            **Exemplo de body:**
            ```json
            { "codAgente": 1, "codFundo": 1, "vlrOperacao": 500000.00,
              "dataFormalizacao": "2026-04-01", "tipoPessoa": 2 }
            ```
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Operação inserida"),
        @APIResponse(responseCode = "400", description = "codAgente ausente"),
        @APIResponse(responseCode = "500", description = "Erro no DB2 (ex: FK inválida)")
    })
    public Response inserirOperacao(Map<String, Object> body) {
        Object codAgtObj = body.get("codAgente");
        if (codAgtObj == null) {
            return Response.status(400).entity(Map.of("erro", "codAgente é obrigatório")).build();
        }
        int     codAgente        = ((Number) codAgtObj).intValue();
        Integer codFundo         = body.get("codFundo")         != null ? ((Number) body.get("codFundo")).intValue()         : null;
        Integer codPrograma      = body.get("codPrograma")      != null ? ((Number) body.get("codPrograma")).intValue()      : null;
        Integer tipoPessoa       = body.get("tipoPessoa")       != null ? ((Number) body.get("tipoPessoa")).intValue()       : null;
        BigDecimal vlrOperacao   = body.get("vlrOperacao")      != null ? new BigDecimal(body.get("vlrOperacao").toString()) : null;
        BigDecimal cpfCnpj       = body.get("cpfCnpj")          != null ? new BigDecimal(body.get("cpfCnpj").toString())     : null;
        LocalDate  dtFrmz        = body.get("dataFormalizacao") != null ? LocalDate.parse(body.get("dataFormalizacao").toString()) : null;
        try {
            long id = individual.inserirOperacao(codAgente, codFundo, codPrograma, vlrOperacao, dtFrmz, tipoPessoa, cpfCnpj);
            return Response.status(201).entity(Map.of(
                    "mensagem",   "Operação inserida com sucesso",
                    "id",         id,
                    "codAgente",  codAgente
            )).build();
        } catch (Exception e) {
            LOG.errorf(e, "[SEED-IND] Falha ao inserir operação (agente=%d)", codAgente);
            return Response.serverError().entity(Map.of("erro", e.getMessage())).build();
        }
    }

    @POST
    @Path("/remessa")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Insere uma remessa individual",
        description = """
            Insere uma linha em `RMS_AGT_FNCO` com status `Recebida`. O ID é gerado
            automaticamente (MAX + 1).

            **Campos obrigatórios:** `codAgente`

            **Campos opcionais:**
            - `codFundo` — padrão `1`
            - `data` — formato `YYYY-MM-DD` (padrão hoje)
            - `qtdReg` — quantidade de registros no lote (padrão aleatório 10k-50k)

            **Exemplo de body:**
            ```json
            { "codAgente": 1, "codFundo": 2, "data": "2026-04-20", "qtdReg": 25000 }
            ```
            """
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Remessa inserida"),
        @APIResponse(responseCode = "400", description = "codAgente ausente"),
        @APIResponse(responseCode = "500", description = "Erro no DB2")
    })
    public Response inserirRemessa(Map<String, Object> body) {
        Object codAgtObj = body.get("codAgente");
        if (codAgtObj == null) {
            return Response.status(400).entity(Map.of("erro", "codAgente é obrigatório")).build();
        }
        int       codAgente = ((Number) codAgtObj).intValue();
        Integer   codFundo  = body.get("codFundo") != null ? ((Number) body.get("codFundo")).intValue() : null;
        Integer   qtdReg    = body.get("qtdReg")   != null ? ((Number) body.get("qtdReg")).intValue()   : null;
        LocalDate data      = body.get("data")      != null ? LocalDate.parse(body.get("data").toString()) : null;
        try {
            long id = individual.inserirRemessa(codAgente, codFundo, data, qtdReg);
            return Response.status(201).entity(Map.of(
                    "mensagem",  "Remessa inserida com sucesso",
                    "id",        id,
                    "codAgente", codAgente
            )).build();
        } catch (Exception e) {
            LOG.errorf(e, "[SEED-IND] Falha ao inserir remessa (agente=%d)", codAgente);
            return Response.serverError().entity(Map.of("erro", e.getMessage())).build();
        }
    }
}
