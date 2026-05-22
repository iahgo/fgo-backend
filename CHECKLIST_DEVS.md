# Checklist — Replicar o Painel Agentes FGO (4 devs, prazo apertado)

> Estratégia: dividir por vertical. Cada dev pega uma faixa de endpoints e entrega de ponta a ponta (repository → service → resource → teste manual no Swagger).

---

## Divisão de trabalho sugerida

| Dev | Escopo | Endpoints |
|---|---|---|
| **Dev 1** | Backend infra + fundos + programas + painel KPIs | ep. 1–9 |
| **Dev 2** | Operações + Remessas | ep. 10–15 |
| **Dev 3** | Movimentações + Pendências | ep. 16–22 |
| **Dev 4** | Frontend Angular, integração, autenticação JWT, deploy |  |

---

## FASE 0 — Setup (Dev 1 configura, compartilha com o time) — Dia 1

- [ ] Criar projeto Quarkus 3.x com as extensões:
  ```
  quarkus-resteasy-reactive-jackson
  quarkus-hibernate-orm
  quarkus-jdbc-db2
  quarkus-redis-client
  quarkus-smallrye-openapi
  quarkus-smallrye-health
  ```
- [ ] Configurar `application.properties`:
  - `quarkus.datasource.db-kind=db2`
  - `quarkus.datasource.jdbc.url=jdbc:db2://<host>:50000/<database>`
  - `quarkus.datasource.username` / `password`
  - `quarkus.redis.hosts=redis-sentinel://...`
  - `quarkus.hibernate-orm.database.generation=none`
- [ ] Criar `PageDto<T>` genérico (content, page, size, totalElements, totalPages)
- [ ] Criar `FiltroItemDto` (codigo, label)
- [ ] Configurar `GlobalExceptionMapper` para padronizar erros
- [ ] Confirmar acesso ao DB2 e listar schemas: `DB2D4W` e `DB2GFG`
- [ ] Subir no Git com branch strategy: `main` (produção), `develop`, feature branches por dev

---

## FASE 1 — Backend (Devs 1, 2, 3 em paralelo) — Dias 1–10

### Dev 1 — Fundos, Programas e Painel KPIs (ep. 1–9)

**Repositório:**
- [ ] `PainelRepository` com queries nativas em `DB2D4W.CTRA_FNDO_GRTR` (filtro `DT_REF = MAX`)
  - `buscarFundosPorAgente` → DISTINCT CD_FNDO_GRTR + NM_FNDO_GRTR
  - `buscarProgramasPorAgente` → DISTINCT CD_TIP_PGM_CRD + CASE nome (Pronampe Solidário RS)
  - `buscarInformacoesGerais` → COUNT DISTINCT mutuários, COUNT ops, SUM/AVG valores
  - `buscarInadimplencia` → SUM VL_SDO_CPTL_ATR, SUM VL_SDO_CPTL_NMLD, COUNT ops atraso
  - `buscarIvhPorPrograma` → AVG cobertura, SUM honrados/recuperados/contratado por programa
  - `buscarIvhSerieHistorica` → YEAR/MONTH(DT_FRMZ_OPR), SUM VL_GRT / VL_OPR
  - `buscarRemessasResumoPorStatus` → `DB2GFG.RMS_AGT_FNCO`, GROUP BY CD_TIP_EST_RMS
  - `buscarPendenciasAgregado` → `DB2D4W.DETT_OPR_PND`, GROUP BY NM_TIP_PNC_OPR_CRD
  - `buscarMovimentacaoSerie` → `RMS_AGT_FNCO`, CD_TIP_NTZ_MVTC IN (1,2), SUM VL_LQDO_MVTC_RMS
  - `buscarCarteiraFundoSerie` → `CTRA_FNDO_GRTR`, SUM VL_SDO_CPTL_NMLD por mês

**DTOs** (criar em `dto/painel/`):
- [ ] `FundoDto(cdFundo, nmFundo)`
- [ ] `ProgramaDto(cdPrograma, nmPrograma, cdFundo)`
- [ ] `InformacoesGeraisDto(mutuarios, operacoes, saldoContratado, ticketMedio, saldoCarteira)`
- [ ] `InadimplenciaDto(saldoAtrasado, indiceInadimplenciaSaldo, indiceInadimplenciaOperacoes)`
- [ ] `IvhItemDto(cdPrograma, nmPrograma, coberturaMedia, vlHonrados, vlRecuperados, vlContratado, ivh)`
- [ ] `IvhSerieDto(List<PeriodoIvhDto>)` onde `PeriodoIvhDto(periodo, ivh)`
- [ ] `RemessasResumoDto(total, naoMovimentadas, concluidas)`
- [ ] `PendenciasResumoDto(List<PendenciasGrupoDto>)` onde `PendenciasGrupoDto(tipo, label, valor)`
- [ ] `MovimentacaoSerieDto(List<PeriodoMovimentacaoDto>)` onde `PeriodoMovimentacaoDto(periodo, carteiraAgente, carteiraFundo)`

**Service:** `PainelService` — mapear `Object[]` para DTOs, calcular IVH e índices de inadimplência

**Resources:** `FundoResource`, `ProgramaResource`, `PainelResource` com `@Tag` e `@Operation`

- [ ] Testar todos os 9 endpoints no Swagger UI com agente real

---

### Dev 2 — Operações + Remessas (ep. 10–15)

**Operações (ep. 10–11):**
- [ ] `OperacaoListagemRepository` — native SQL em `DB2D4W.CTRA_FNDO_GRTR`
  - WHERE: `DT_REF = MAX AND CD_FNDO_GRTR <> 1 AND CD_AGT_FNCO = ?`
  - Filtros: cdFundo, cdPrograma, nrContrato (LIKE)
  - CASE Pronampe Solidário RS 1 (2023-11-03 a 2023-12-31) e RS 2 (2024-05-29 a 2024-12-31)
  - Métodos: `listar()`, `contar()`, `listarTodos()`
- [ ] `OperacaoItemDto(nmFundo, nmPrograma, nrOperacao, publicoAlvo, estadoOperacao, dataFormal, dataVencimento, valorOperacao, valorLiberado)`
- [ ] `OperacaoListagemService` — mapeamento Object[] → DTO
- [ ] `OperacoesPainelResource` — `GET /api/v1/operacoes` e `GET /api/v1/operacoes/exportar` (CSV)

**Remessas (ep. 12–15):**
- [ ] `RemessaListagemRepository` — native SQL em `DB2GFG.RMS_AGT_FNCO`
  - JOINs: FNDO_GRTR, AGT_FNCO_FNDO_GRTR, AGT_FNCO, TIP_EST_RMS, TIP_MTV_RJC_RMS
  - SELECT 14 colunas: ANO_MES (CAST DATE), TS_RCBT_RMS, CD_CNP2, NM_ABVD_AGT_FNCO, CD_FNDO_GRTR, SG_FNDO_GRTR, NR_SEQL_RMS, CD_TIP_EST_RMS, NM_TIP_EST_RMS, CD_MTV_RJC_RMS, TX_TIP_MTV_RJC_RMS, QT_REG_RMS, QT_REG_ACT (CASE EVT_OPR_R3TD), QF_RMS_RJC
  - QT_REG_ACT: `CASE WHEN CD_TIP_EST_RMS IN (5,6) THEN QT_REG_RMS - 2 - COUNT(EVT_OPR_R3TD) ELSE 0`
  - Filtros: cdFundo, cdTipEstRms, cdMtvRjcRms, nrSequencial
- [ ] `RemessaItemDto(referencia, dataHoraRecebimento, nrSequencial, agenteFinanceiro, nmFundo, situacao, motivoRejeicao, qtdeRegistros, registrosAceitos, registrosRecusados)`
- [ ] `RemessaListagemService` — mapeamento + formatar referencia como MM/yyyy
- [ ] `RemessaListagemResource` — 4 endpoints incluindo filtros estáticos (12–15) + exportar CSV

- [ ] Testar todos os 6 endpoints no Swagger UI

---

### Dev 3 — Movimentações + Pendências (ep. 16–22)

**Movimentações (ep. 16–19):**
- [ ] `MovimentacaoRepository` — native SQL em `DB2GFG.RMS_AGT_FNCO`
  - WHERE: `CD_TIP_NTZ_MVTC IN (1, 2) AND CD_FNDO_GRTR <> 1`
  - JOINs: FNDO_GRTR, AGT_FNCO_FNDO_GRTR, AGT_FNCO, TIP_MTV_RJC_RMS, TIP_EST_RMS
  - SELECT 10 colunas: NM_FNDO_GRTR, NR_SEQL_RMS, CASE tipo (NTZ_MVTC), NM_TIP_EST_RMS, TX_TIP_MTV_RJC_RMS, DT_PRCT_EVT, DT_ATL_MNTR, DT_MVTC_FNCR, QT_REG_RMS, VL_LQDO_MVTC_RMS
  - Detalhe: `DB2GFG.RSM_MVTC_FNCR_RMS JOIN RMS_AGT_FNCO JOIN TIP_MVTC_FNCR`
- [ ] `MovimentacaoItemDto` + `MovimentacaoDetalheDto`
- [ ] `MovimentacaoService` + `MovimentacaoResource` (4 endpoints + filtros situações)

**Pendências (ep. 20–22):**
- [ ] `PendenciaRepository` — native SQL em `DB2D4W.DETT_OPR_PND`
  - JOINs: FNDO_GRTR, TIP_PGM_CRD, AGT_FNCO_FNDO_GRTR, AGT_FNCO
  - SELECT: NM_FNDO_GRTR, CASE NM_ABVD_TIP_PGM, CD_IDFR_EXNO_OPR, CD_TIP_EST_OPR, NM_TIP_PNC_OPR_CRD, DT_SNC_PHC
  - Filtro tipoPendencia: match exato no texto NM_TIP_PNC_OPR_CRD
  - Métodos: `listar()`, `contar()`, `listarTodos()`
  - Para filtros/tipos: query DISTINCT NM_TIP_PNC_OPR_CRD da mesma tabela
- [ ] `PendenciaItemDto(nmFundo, nmPrograma, nrContrato, situacaoContrato, tipoPendencia, dataInicioPendencia)`
- [ ] `PendenciaService` + `PendenciaResource` (3 endpoints + filtros tipos)

- [ ] Testar todos os 7 endpoints no Swagger UI

---

### Dev 4 — Frontend Angular + Auth + Deploy

**Frontend Angular:**
- [ ] Configurar projeto Angular com rota base `/painel`
- [ ] Serviço `ApiService` com `HttpClient`, URL base configurável por ambiente
- [ ] Componentes principais:
  - [ ] Seletor de fundo/programa (usa ep. 1 e 2)
  - [ ] Cards KPIs: informações gerais, inadimplência, remessas resumo, pendências resumo (ep. 3, 4, 7, 8)
  - [ ] Gráfico IVH série histórica (ep. 6)
  - [ ] Gráfico movimentação financeira série (ep. 9)
  - [ ] Tabela IVH por programa (ep. 5)
  - [ ] Tabela operações paginada com filtros (ep. 10) + botão exportar (ep. 11)
  - [ ] Tabela remessas paginada com filtros (ep. 14) + exportar (ep. 15)
  - [ ] Tabela movimentações com detalhe (ep. 17, 18) + exportar (ep. 19)
  - [ ] Tabela pendências paginada com filtros (ep. 21) + exportar (ep. 22)
- [ ] Interceptor HTTP: injetar `cdAgtFnco` temporariamente (substituir por JWT)

**Autenticação:**
- [ ] Definir estratégia JWT com o time (Keycloak? IIB gateway?)
- [ ] Quando disponível: substituir `?cdAgtFnco=` por extração do token em todos os resources

**Deploy:**
- [ ] Dockerfile do backend (Quarkus JVM)
- [ ] Manifesto OpenShift: Deployment, Service, Route
- [ ] Secret com credenciais DB2 e Redis
- [ ] GitHub Actions: build → push registry → `oc set image`
- [ ] Health check: `/q/health/live` e `/q/health/ready`

---

## FASE 2 — Validação com DB2 real (Dev 1 + Dev 2 + Dev 3) — Dias 10–15

- [ ] Executar cada endpoint contra DB2 real (não mock)
- [ ] Verificar nomes de colunas críticos:
  - `TX_TIP_MTV_RJC_RMS` (pode ser `NM_` no DB2 real)
  - `TS_RCBT_RMS` (timestamp de recebimento da remessa)
  - `VL_LQDO_MVTC_RMS` (valor líquido da movimentação)
  - `CD_IDFR_EXNO_OPR` em `CTRA_FNDO_GRTR` (confirmar campo de nrContrato)
  - `NM_TIP_PNC_OPR_CRD` em `DETT_OPR_PND` (confirmar nome exato)
- [ ] Validar resultados contra telas do BI original (comparar números)
- [ ] Medir tempo de resposta das queries mais pesadas:
  - `BASE_ANL_OPR` (custo 261.810) — considerar índice em DT_REF + CD_AGT_FNCO
  - `PENDENCIAS` (custo 48.472) — verificar se DETT_OPR_PND já tem índice por agente/fundo
- [ ] Se queries lentas: avaliar cache Redis com TTL curto (ex: 5 min para listagens)

---

## FASE 3 — Testes e homologação — Dias 15–18

- [ ] Testes de integração: cada endpoint com dados reais de pelo menos 2 agentes distintos
- [ ] Comparar respostas com as telas do BI original (screenshots)
- [ ] Testar exportação CSV de cada endpoint (operações, remessas, movimentações, pendências)
- [ ] Testar paginação: page=0, page=1, size=10, size=100
- [ ] Testar filtros: todos os campos de filtro isoladamente e combinados
- [ ] Smoke test de deploy no ambiente de homologação

---

## FASE 4 — Go live — Dia 20

- [ ] Deploy em produção via GitHub Actions
- [ ] Verificar health checks
- [ ] Monitorar logs por 30 min após deploy
- [ ] Validação final com usuário de negócio

---

## Pontos de atenção

| Item | Risco | Mitigação |
|---|---|---|
| Nomes de coluna DB2 | Alto — podem divergir do BI | Validar com `DESC TABLE` antes de escrever queries |
| Performance queries analytics | Alto — tabelas 100M+ rows | Testar com EXPLAIN antes de ir para prod |
| `DT_REF = MAX` em CTRA_FNDO_GRTR | Médio — subquery em cada request | Considerar materializar o MAX em Redis/cache |
| `CD_TIP_NTZ_MVTC IN (1,2)` em RMS_AGT_FNCO | Médio — confirmar códigos corretos | Consultar dicionário de dados do BI |
| Auth cdAgtFnco via query param | Alto — não ir para prod assim | Bloquear no IIB gateway enquanto JWT não está pronto |
| Pronampe Solidário datas hardcoded | Baixo | Confirmar se as datas do BI (nov/2023 e mai/2024) são as definitivas |
