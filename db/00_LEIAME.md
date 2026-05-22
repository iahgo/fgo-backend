# Scripts SQL — Painel Agentes FGO

## Ordem de execução

```bash
# Execute como usuário com permissão nos schemas DB2GFG e DB2D4W
# Substitua <DATABASE> pelo nome do banco (ex: DCG1)

db2 connect to <DATABASE> user <USER> using <PASSWORD>

db2 -tvf 01_DDL_DB2GFG_NOVAS_TABELAS.sql
db2 -tvf 01b_DDL_RMS_AGT_FNCO_COMPLETO.sql   # só se recriar do zero
db2 -tvf 02_DDL_DB2D4W.sql
db2 -tvf 03_SEED_LOOKUP.sql
db2 -tvf 04_SEED_DADOS_TESTE.sql
```

Ou num único comando:
```bash
for f in 01 01b 02 03 04; do
  db2 -tvf ${f}_*.sql
done
```

## O que cada script faz

| Arquivo | O que faz |
|---|---|
| `01_DDL_DB2GFG_NOVAS_TABELAS.sql` | Cria `TIP_EST_RMS`, `TIP_MTV_RJC_RMS`, `TIP_MVTC_FNCR`, `EVT_OPR_R3TD`, `RSM_MVTC_FNCR_RMS` + ALTER em `RMS_AGT_FNCO` |
| `01b_DDL_RMS_AGT_FNCO_COMPLETO.sql` | `RMS_AGT_FNCO` completo — usar só se recriar do zero |
| `02_DDL_DB2D4W.sql` | Cria schema analítico: `CTRA_FNDO_GRTR` e `DETT_OPR_PND` |
| `03_SEED_LOOKUP.sql` | Popula tabelas de domínio (situações, motivos, tipos) |
| `04_SEED_DADOS_TESTE.sql` | Insere dados de teste para todos os 22 endpoints |

## Dados de teste inseridos

| Tabela | Registros | Agentes | Fundos |
|---|---|---|---|
| `CTRA_FNDO_GRTR` | 22 rows + 1 antigo | 1 (BB), 2 (CEF), 3 (Bradesco) | 2 (FGI), 3 (PEAC) |
| `DETT_OPR_PND` | 17 rows | 1, 2, 3 | 2, 3 |
| `RMS_AGT_FNCO` | 11 rows | 1, 2, 3 | 2 |
| `EVT_OPR_R3TD` | 5 rows | 1, 3 | 2 |
| `RSM_MVTC_FNCR_RMS` | 16 rows | 1, 2, 3 | 2 |

## Como testar no Swagger UI

Abra `http://localhost:8080/q/swagger-ui` e use:

```
cdAgtFnco = 1   (Banco do Brasil — mais dados)
cdFundo   = 2   (FGI)
cdFundo   = -1  (todos os fundos)
cdPrograma = -1 (todos os programas)
cdPrograma = 1  (PRONAMPE)
cdPrograma = 42 (Pronampe Solidário RS)
```

## Atenção — nomes de coluna

O `SeedService.java` antigo usava nomes incorretos. Os scripts aqui usam os nomes corretos do BI:

| Nome antigo (SeedService) | Nome correto (BI) |
|---|---|
| `TS_RCT_RMS` | `TS_RCBT_RMS` |
| `VL_LODO_MVTC_RMS` | `VL_LQDO_MVTC_RMS` |

Veja `05_FIX_SEMENTE_SERVICE.md` para instruções de atualização do SeedService.
