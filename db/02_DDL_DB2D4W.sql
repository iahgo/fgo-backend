-- =============================================================================
-- DDL — Schema DB2D4W (Data Warehouse analytics)
-- Painel Agentes FGO · 2026-05-22
-- =============================================================================
-- Tabelas analíticas pré-computadas — substituem JPQL nas queries do BI.
-- =============================================================================

-- Criar schema se não existir (execute como SYSADM):
-- CREATE SCHEMA DB2D4W;

-- =============================================================================
-- 1. CTRA_FNDO_GRTR — Carteira do Fundo Garantidor (snapshot analítico)
--    Query de referência: BASE_ANL_OPR do BI (custo D$64: 261.810)
--    Chave de snapshot: DT_REF — filtre sempre por DT_REF = (SELECT MAX(Y.DT_REF) FROM ...)
-- =============================================================================
CREATE TABLE DB2D4W.CTRA_FNDO_GRTR (
    -- Chave de snapshot
    DT_REF              DATE          NOT NULL,

    -- FKs desnormalizadas
    CD_FNDO_GRTR        SMALLINT      NOT NULL,
    CD_AGT_FNCO         INTEGER       NOT NULL,
    CD_TIP_PGM_CRD      SMALLINT,

    -- Identificadores
    CD_IDFR_EXNO_OPR    CHAR(20),               -- número externo do contrato (LIKE filter)
    CD_IDFR_SRF         DECIMAL(14,0),           -- CPF/CNPJ do mutuário (COUNT DISTINCT)

    -- Campos desnormalizados (evitam JOINs nas queries analíticas)
    NM_ABVO_TIP_PGM     VARCHAR(100),            -- nome do programa (com lógica Pronampe RS)
    NM_TIP_PBCO_ALVO    VARCHAR(80),             -- público alvo do contrato
    NM_TIP_EST_OPR      VARCHAR(80),             -- estado da operação

    -- Datas
    DT_FRMZ_OPR         DATE,                    -- data de formalização (base para séries temporais)
    DT_VNCT_OPR         DATE,                    -- data de vencimento

    -- Valores financeiros
    VL_OPR_CRD          DECIMAL(15,2),           -- valor da operação contratado
    VL_TTL_LIBD_OPR     DECIMAL(15,2),           -- valor total liberado
    VL_SDO_CPTL_NMLD    DECIMAL(15,2),           -- saldo capital normalizado (carteira)
    VL_SDO_CPTL_ATR     DECIMAL(15,2) DEFAULT 0, -- saldo capital em atraso (inadimplência)

    -- Garantia
    PC_GRT_OPR_CRD      DECIMAL(7,4)  DEFAULT 0, -- percentual de cobertura da garantia
    VL_GRT_OPR_AJSD     DECIMAL(15,2),           -- valor de garantia ajustado (contratado IVH)
    VL_MVTC_HNR_GRT     DECIMAL(15,2) DEFAULT 0, -- movimentação de honra bruta
    VL_MVTC_DVLC_HNRD   DECIMAL(15,2) DEFAULT 0, -- devolução de honra
    VL_MVTC_RCPD_HNRD   DECIMAL(15,2) DEFAULT 0, -- movimentação de recuperação
    VL_MVTC_DVLC_RCPD   DECIMAL(15,2) DEFAULT 0  -- devolução de recuperação
);

-- Índices para filtros mais comuns das queries do BI
CREATE INDEX DB2D4W.IX_CTRA_REF_AGT  ON DB2D4W.CTRA_FNDO_GRTR (DT_REF, CD_AGT_FNCO, CD_FNDO_GRTR);
CREATE INDEX DB2D4W.IX_CTRA_FRMZ     ON DB2D4W.CTRA_FNDO_GRTR (DT_FRMZ_OPR, CD_AGT_FNCO);

-- =============================================================================
-- 2. DETT_OPR_PND — Detalhe de Operações com Pendência (pré-computada)
--    Query de referência: PENDENCIAS do BI (custo D$64: 48.472)
--    Não tem chave de snapshot — tabela sempre atual.
-- =============================================================================
CREATE TABLE DB2D4W.DETT_OPR_PND (
    -- Identificadores
    CD_FNDO_GRTR        SMALLINT      NOT NULL,
    CD_AGT_FNCO         INTEGER       NOT NULL,
    CD_TIP_PGM_CRD      SMALLINT,
    CD_IDFR_EXNO_OPR    CHAR(20),

    -- Estado e pendência
    CD_TIP_EST_OPR      SMALLINT,
    NM_TIP_PNC_OPR_CRD  VARCHAR(120),            -- tipo de pendência — texto já resolvido
    DT_SNC_PHC          DATE                     -- data de início da pendência
);

CREATE INDEX DB2D4W.IX_DETT_PND_AGT ON DB2D4W.DETT_OPR_PND (CD_AGT_FNCO, CD_FNDO_GRTR);
CREATE INDEX DB2D4W.IX_DETT_PND_TIP ON DB2D4W.DETT_OPR_PND (NM_TIP_PNC_OPR_CRD);
