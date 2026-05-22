# Painel Agentes FGO — Backend

Microserviço do **Painel do Agente Financeiro** do FGO (Fundo de Garantia de Operações).  
Desenvolvido em **Quarkus 3.x + Redis Sentinel + IBM DB2**, rodando em **OpenShift Local (CRC)** em servidor RHEL 9.

> **[→ Documentação completa dos 22 endpoints (HTML)](endpoints.html)** — abrir no navegador para visualizar com formatação.
>
> **[→ Diagramas de Arquitetura N0/N1/N2/N3](docs/diagramas/index.html)** — Contexto · Containers · Componentes · Sequências
>
> **[→ Scripts SQL — DDL + Seed de teste](db/00_LEIAME.md)** — Criar tabelas e popular dados para testes no Swagger

---

## Links de acesso público (internet)

> Expostos via **Tailscale Funnel** — URL fixa `*.ts.net`, sem VPN.

| Recurso | URL pública |
|---|---|
| **Swagger UI** | https://localhost-1.tail061a64.ts.net/q/swagger-ui |
| **Health** | https://localhost-1.tail061a64.ts.net/q/health |
| **Métricas do sistema** | https://localhost-1.tail061a64.ts.net/admin/sistema |
| **Logs do pod** | https://localhost-1.tail061a64.ts.net/admin/logs |
| **Console OpenShift** | https://localhost-1.tail061a64.ts.net/console |

### Usuários do OpenShift

Login no Console: acesse a URL acima → selecione **fgo-htpasswd** → entre com usuário e senha abaixo.

| Usuário | Senha | Nível de acesso |
|---|---|---|
| `fgo-admin` | `FgoAdmin2026!` | Cluster-admin — acesso total |
| `fgo-dev` | `FgoDev2026!` | Edit em `fgo-backend` — deploy e logs |
| `fgo-viewer` | `FgoView2026!` | View em `fgo-backend` — somente leitura |
| `log-viewer` | `FgoLog2026!` | View em `fgo-backend` — somente leitura |

```bash
oc login --server=https://api.crc.testing:6443 -u fgo-dev -p 'FgoDev2026!'
```

---

## Links internos (rede local / Tailscale)

| Recurso | URL |
|---|---|
| **Swagger UI (local)** | `https://ms-operacao-fgo-backend.apps-crc.testing/q/swagger-ui` |
| **Console OpenShift (local)** | `https://console-openshift-console.apps-crc.testing` |

---

## Arquitetura

```
Angular  →  IIB (gateway BB)  →  MS Painel FGO  →  Redis Sentinel (cache KPIs)
                                       ↓
                                    IBM DB2
                             DB2D4W (analytics)
                             DB2GFG (transacional)
```

### Schemas DB2

| Schema | Tipo | Tabelas principais |
|---|---|---|
| `DB2D4W` | Data Warehouse (analytics) | `CTRA_FNDO_GRTR` (operações, snapshot DT_REF=MAX), `DETT_OPR_PND` (pendências pré-computadas) |
| `DB2GFG` | Transacional / domínio | `RMS_AGT_FNCO` (remessas), `RSM_MVTC_FNCR_RMS` (detalhe movimentação), `AGT_FNCO_FNDO_GRTR`, `TIP_PGM_CRD`, `FNDO_GRTR` |

---

## Stack

| Componente | Tecnologia |
|---|---|
| Runtime | Quarkus 3.x (JVM 17) |
| Banco de dados | IBM DB2 for z/OS (queries nativas — sem JPQL nos endpoints BI) |
| Cache | Redis 7 — modo Sentinel (1 master + 2 réplicas + 3 sentinels) |
| Orquestração | OpenShift Local (CRC) / OpenShift 4.18 |
| CI/CD | GitHub Actions — self-hosted runner no servidor RHEL 9 |

---

## API — Endpoints v1 (22 endpoints)

> Documentação detalhada com exemplos de resposta: **[endpoints.html](endpoints.html)**

### Grupos

| Grupo | Base path | Endpoints | Fonte DB2 |
|---|---|---|---|
| Fundos | `/api/v1/fundos` | 1 | `DB2D4W.CTRA_FNDO_GRTR` |
| Programas | `/api/v1/programas` | 1 | `DB2D4W.CTRA_FNDO_GRTR` |
| Painel KPIs | `/api/v1/painel` | 7 (ep. 3–9) | `CTRA_FNDO_GRTR`, `DETT_OPR_PND`, `RMS_AGT_FNCO` |
| Operações | `/api/v1/operacoes` | 2 (ep. 10–11) | `DB2D4W.CTRA_FNDO_GRTR` (BASE_ANL_OPR BI) |
| Remessas | `/api/v1/remessas` | 4 (ep. 12–15) | `DB2GFG.RMS_AGT_FNCO` (REMESSAS BI) |
| Movimentações | `/api/v1/movimentacoes` | 4 (ep. 16–19) | `DB2GFG.RMS_AGT_FNCO` (MVTC_FNCR BI) + `RSM_MVTC_FNCR_RMS` |
| Pendências | `/api/v1/pendencias` | 3 (ep. 20–22) | `DB2D4W.DETT_OPR_PND` (PENDENCIAS BI) |

### Convenções de filtros

- `cdAgtFnco` — obrigatório em todos. **TODO:** substituir por JWT.
- `-1` para int = sem filtro (todos os fundos / programas / situações).
- `null` para string = sem filtro.
- `page` 0-based, `size` aceita apenas 10, 50 ou 100.

---

## Pendências técnicas (TODOs)

| # | Item | Prioridade |
|---|---|---|
| 1 | Substituir `?cdAgtFnco=` por contexto JWT em todos os endpoints | Alta |
| 2 | Verificar nomes de coluna no DB2 real: `TX_TIP_MTV_RJC_RMS`, `TS_RCBT_RMS`, `VL_LQDO_MVTC_RMS` | Alta |
| 3 | `MovimentacaoResource.detalhe()` — path param semântico: deve usar PK `CD_RMS_AGT_FNCO`, não `NR_SEQL_RMS` | Média |
| 4 | Diagrama de arquitetura N0/N1/N2/N3 (decisões de design) | Baixa |
| 5 | Endpoint `/api/operacoes/lista` legado (DB2GFG) — avaliar remoção após validação dos v1 | Baixa |

---

## Como rodar localmente

```bash
# Pré-requisitos: Java 17+, Maven 3.9+, DB2 acessível

# Configure conexão DB2
# DB2_HOST=<ip>  DB2_PORT=50000  DB2_DATABASE=DCG1  DB2_USER=...  DB2_PASSWORD=...

# Iniciar (Redis sobe automaticamente via Dev Services)
mvn quarkus:dev

# Swagger UI disponível em:
# http://localhost:8080/q/swagger-ui
```

---

## CI/CD

```
push main
   ↓
[Job 1] mvn test
   ↓
[Job 2] Build JAR → Podman build → Push registry OpenShift
   ↓
oc set image deployment → Rolling update (zero downtime)
   ↓
Smoke test GET /q/health/live
```

**Secret necessário:** `OPENSHIFT_TOKEN` em Settings → Secrets → Actions.

---

## Estrutura de pacotes

```
com.example
├── dto/
│   ├── painel/          DTOs dos KPIs (FundoDto, IvhItemDto, InadimplenciaDto, ...)
│   └── listagem/        DTOs das listagens (OperacaoItemDto, RemessaItemDto, ...)
├── repository/
│   ├── PainelRepository            Queries nativas para os KPIs do painel
│   ├── OperacaoListagemRepository  BASE_ANL_OPR (DB2D4W.CTRA_FNDO_GRTR)
│   ├── RemessaListagemRepository   REMESSAS BI (DB2GFG.RMS_AGT_FNCO)
│   ├── MovimentacaoRepository      MVTC_FNCR + DET_MVT_FNCR BI
│   └── PendenciaRepository         PENDENCIAS BI (DB2D4W.DETT_OPR_PND)
├── service/
│   ├── PainelService, OperacaoListagemService
│   ├── RemessaListagemService, MovimentacaoService, PendenciaService
└── resource/
    ├── FundoResource, ProgramaResource, PainelResource
    ├── OperacoesPainelResource, RemessaListagemResource
    ├── MovimentacaoResource, PendenciaResource
    └── (legacy) OperacaoListagemResource, OperacaoResource, RemessaResource
```
