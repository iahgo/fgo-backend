# MS Operação FGO — Backend

Microserviço de KPIs de Operações de Crédito do Fundo de Garantia de Operações (FGO).  
Desenvolvido em **Quarkus 3.8.4 + Redis Sentinel + IBM DB2**, rodando em **OpenShift Local (CRC)** em servidor RHEL 9.

---

## 🔗 Links de acesso público (internet)

> Expostos via **Cloudflare Tunnel** — sem VPN, sem Tailscale, direto do navegador.  
> Configure com `scripts/setup-cloudflare-tunnel.sh SEU-DOMINIO.com`

| Recurso | URL pública |
|---|---|
| **Swagger UI** | `https://swagger.SEU-DOMINIO.com/q/swagger-ui` |
| **Console OpenShift** | `https://openshift.SEU-DOMINIO.com` |
| **Health** | `https://api.SEU-DOMINIO.com/q/health` |
| **Métricas do sistema** | `https://api.SEU-DOMINIO.com/admin/sistema` |
| **Logs do pod** | `https://api.SEU-DOMINIO.com/admin/logs` |

### Usuários do OpenShift

> Crie com `scripts/setup-openshift-users.sh` no servidor.

| Usuário | Senha | Nível de acesso |
|---|---|---|
| `fgo-admin` | `FgoAdmin2026!` | Cluster-admin — acesso total |
| `fgo-dev` | `FgoDev2026!` | Edit em `fgo-backend` — deploy e logs |
| `fgo-viewer` | `FgoView2026!` | View em `fgo-backend` — somente leitura |
| `log-viewer` | `FgoLog2026!` | View em `fgo-backend` — somente leitura |

Login: selecione **fgo-htpasswd** no console e entre com usuário + senha acima.

---

## 🔗 Links internos (rede local / Tailscale)

| Recurso | URL |
|---|---|
| **Swagger UI (local)** | `https://ms-operacao-fgo-backend.apps-crc.testing/q/swagger-ui` |
| **Console OpenShift (local)** | `https://console-openshift-console.apps-crc.testing` |

> URL exata: `oc get route ms-operacao -n fgo-backend -o jsonpath='{.spec.host}'`

---

## Arquitetura

```
Angular  →  IIB (gateway BB)  →  MS Operação  →  Redis Sentinel
                                      ↑                  ↑
                                    warm-up          serve < 1ms
                                      ↓
                                    DB2 12.1
                               (100M operações, 35 GB)
```

### Fluxo de dados

1. **Warm-up** (07:20 diário + startup): queries de agregação no DB2 → JSON compacto gravado no Redis (TTL 25h)
2. **Requisição do agente**: `GET /api/operacoes/{mesAno}` → Redis GET → resposta < 1ms — DB2 não é tocado
3. **Lock distribuído**: `SET NX` no Redis garante que apenas 1 pod carregue cada agente, mesmo com 3 réplicas

### Sobre o Redis sobrescrever dados no startup

Sim, é intencional. Cada vez que um pod sobe, o warm-up roda em background e sobrescreve o Redis com dados frescos do DB2. O lock distribuído evita que múltiplos pods façam isso ao mesmo tempo. O DB2 é a fonte da verdade; o Redis é o cache. O TTL de 25h garante que os dados persistam entre restarts normais.

---

## Stack

| Componente | Tecnologia |
|---|---|
| Runtime | Quarkus 3.8.4 (JVM 17) |
| Banco de dados | IBM DB2 12.1 (em container Podman) |
| Cache | Redis 7 — modo Sentinel (1 master + 2 réplicas + 3 sentinels) |
| Orquestração | OpenShift Local (CRC) 2.60 / OpenShift 4.18 |
| CI/CD | GitHub Actions — self-hosted runner no servidor RHEL 9 |
| Build de imagem | Podman (sem Docker daemon) |
| Registry | OpenShift internal registry (localhost) |

---

## API — Endpoints

### Operações de Crédito

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/operacoes/{mesAno}` | KPIs do agente para o mês (Redis, < 1ms) |
| `GET` | `/api/operacoes/{mesAno}/consolidado` | Visão admin: todos os agentes por programa e ranking (DB2 direto, lento) |

> **Header obrigatório:** `X-Cod-Agente: <cod>` — em produção injetado pelo IIB a partir do token do usuário.

### Agentes Financeiros

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/agentes` | Lista os 40 agentes financeiros cadastrados |

### Remessas

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/remessas` | Remessas do agente (últimas 50, `?limite=N`) |
| `GET` | `/api/remessas/{id}` | Detalhe de uma remessa |

### Admin — Reload de Cache

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/operacoes/admin/reload` | Recarrega Redis de todos os agentes (background) |
| `POST` | `/api/operacoes/admin/reload/{codAgente}` | Recarrega um agente específico |

### Admin — Monitoramento *(somente leitura)*

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/admin/sistema` | CPU, RAM, disco, JVM, Redis INFO, tamanho das tabelas DB2 |
| `GET` | `/admin/logs` | Últimas 2.000 linhas de log deste pod (`?limite=N&nivel=ERROR`) |
| `GET` | `/admin/seed/status` | Progresso do seed de dados |

### Admin — Seed de Dados ⚠️ *Destrutivos*

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/admin/seed` | **APAGA TUDO** e gera N operações sintéticas (`?quantidade=100000000&limpar=true`) |
| `POST` | `/admin/seed/remessas` | Recria apenas a tabela `RMS_AGT_FNCO` (preserva operações) |

---

## Dados de exemplo

| Tabela | Registros | Tamanho |
|---|---|---|
| `OPR_CRD_FNDO_GRTR` (operações) | ~100.000.000 | ~35 GB |
| `RMS_AGT_FNCO` (remessas) | ~11.800 | < 10 MB |
| `AGT_FNCO` (agentes) | 40 | — |
| `TIP_PGM_CRD` (programas) | 6 | — |
| Redis (cache por agente/mês) | 40 chaves ativas | ~2–5 MB |

**Agentes financeiros cadastrados:** BB, CEF, Bradesco, Itaú, Santander, Sicredi, Sicoob, BTG Pactual, XP, Inter, Nubank, Safra, Votorantim, BMG, Banrisul, BRB, Modal, Pan, Agibank, C6 Bank, Original, Mercantil, Paraná, Alfa, Fibra, Daycoval, Sofisa, Industrial, ABC Brasil, Rabobank, JPMorgan, Goldman Sachs, BNP Paribas, Deutsche Bank, Citibank, HSBC, Pine, Neon, PicPay, Will.

**Programas de crédito:** PRONAMPE, FGI, PEAC, Emergencial Investimento, FGO Rural, FGO Geral.

---

## Observabilidade

### Logs por requisição
Cada requisição gera dois logs INFO no logger `fgo.access`:
```
[REQ][A3F1B2] GET /api/operacoes/2026-04 agente=8
[RES][A3F1B2] GET /api/operacoes/2026-04 → HTTP 200 em 0ms body={...}
[SLOW][A3F1B2] GET /api/operacoes/2026-04/consolidado demorou 8231ms
```

### Ver logs do pod
```bash
# Logs em tempo real de todos os pods
oc logs -f -l app=ms-operacao -n fgo-backend

# Logs de um pod específico
oc logs ms-operacao-<hash> -n fgo-backend

# Via API (não requer CLI)
curl -H "X-Cod-Agente: 1" https://<ROUTE_HOST>/admin/logs?nivel=ERROR
```

### Criar usuário somente-leitura (log-viewer)
```bash
# 1. Aplicar manifesto
oc apply -f openshift/08-log-viewer-rbac.yaml

# 2. Gerar token (válido por 1 ano)
oc create token log-viewer -n fgo-backend --duration=8760h

# 3. Compartilhar o token — o usuário faz login assim:
oc login --server=https://api.crc.testing:6443 --token=<TOKEN>
# Ou acessa o console: https://console-openshift-console.apps-crc.testing
```

---

## Como rodar localmente

### Pré-requisitos
- Java 17+, Maven 3.9+
- DB2 acessível (configure `.env`)
- Redis (levantado automaticamente pelo Quarkus Dev Services via Docker)

```bash
# Clone
git clone https://github.com/iahgo/fgo-backend.git
cd fgo-backend

# Configure o .env (copie o exemplo e preencha)
# cp .env.example .env  (arquivo removido do repo — crie manualmente)
# Conteúdo mínimo:
# DB2_HOST=<ip-do-db2>
# DB2_PORT=50000
# DB2_DATABASE=DCG1
# DB2_USER=db2inst1
# DB2_PASSWORD=<senha>

# Iniciar em modo dev (Redis sobe automaticamente)
mvn quarkus:dev

# Swagger UI disponível em:
# http://localhost:8080/q/swagger-ui
```

### Popular dados de teste
```bash
# Gera 100M de operações (demora ~30min — acompanhe em /admin/seed/status)
curl -X POST "http://localhost:8080/admin/seed?quantidade=100000000&limpar=true"

# Popula remessas diárias sem apagar operações
curl -X POST "http://localhost:8080/admin/seed/remessas"

# Força warm-up do Redis após o seed
curl -X POST -H "X-Cod-Agente: 1" http://localhost:8080/api/operacoes/admin/reload
```

---

## CI/CD

O pipeline GitHub Actions roda **no servidor RHEL 9** (self-hosted runner em `/opt/fgo/runner`):

```
push main
   ↓
[Job 1] Testes unitários (mvn test)
   ↓
[Job 2] Build JAR → Podman build → Push registry interno OpenShift
   ↓
oc set image deployment/ms-operacao → Rolling update (zero downtime)
   ↓
Smoke test GET /q/health/live → Summary no GitHub Actions
```

**Secret necessário:** `OPENSHIFT_TOKEN` em Settings → Secrets → Actions (token da SA `github-deployer`).

---

## Estrutura de pacotes

```
com.example
├── config/       FgoConfig (TTL, lock)
├── domain/       Entidades JPA e objetos de domínio
├── dto/          DTOs de resposta da API
├── exception/    Exceções de negócio + GlobalExceptionMapper
├── filter/       RequestResponseLoggingFilter (req/res + elapsed)
├── health/       RedisHealthCheck, Db2HealthCheck
├── loader/       OperacaoLoader (warm-up, scheduler, reload)
├── log/          LogBuffer + LogBufferHandler (logs em memória)
├── listener/     OperacaoReloadSubscriber (Redis Pub/Sub)
├── mapper/       OperacaoMapper (domain → DTO)
├── repository/   OperacaoRepository (DB2), OperacaoRedisRepository, RemessaRepository
├── resource/     Endpoints REST (Operacao, Agente, Remessa, Seed, Sistema, Log)
└── service/      OperacaoService, AgenteService, RemessaService, ConsolidadoService,
                  SistemaService, SeedService
```
