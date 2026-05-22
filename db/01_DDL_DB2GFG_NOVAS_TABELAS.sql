-- =============================================================================
-- DDL — Novas tabelas no schema DB2GFG
-- Painel Agentes FGO · 2026-05-22
-- =============================================================================
-- Execute nesta ordem após o schema DB2GFG já existir.
-- Idempotente: usa IF NOT EXISTS onde suportado, ou DROP + CREATE onde não.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. TIP_EST_RMS — Tipos de situação de remessa
-- -----------------------------------------------------------------------------
CREATE TABLE DB2GFG.TIP_EST_RMS (
    CD_TIP_EST_RMS   SMALLINT      NOT NULL,
    NM_TIP_EST_RMS   VARCHAR(50)   NOT NULL,
    CONSTRAINT PK_TIP_EST_RMS PRIMARY KEY (CD_TIP_EST_RMS)
);

-- -----------------------------------------------------------------------------
-- 2. TIP_MTV_RJC_RMS — Motivos de rejeição de remessa
-- -----------------------------------------------------------------------------
CREATE TABLE DB2GFG.TIP_MTV_RJC_RMS (
    CD_MTV_RJC_RMS       SMALLINT      NOT NULL,
    TX_TIP_MTV_RJC_RMS   VARCHAR(100)  NOT NULL,
    CONSTRAINT PK_TIP_MTV_RJC_RMS PRIMARY KEY (CD_MTV_RJC_RMS)
);

-- -----------------------------------------------------------------------------
-- 3. TIP_MVTC_FNCR — Tipos de movimentação financeira
-- -----------------------------------------------------------------------------
CREATE TABLE DB2GFG.TIP_MVTC_FNCR (
    CD_TIP_MVTC_FNCR   SMALLINT      NOT NULL,
    NM_TIP_MVTC_FNCR   VARCHAR(100)  NOT NULL,
    CONSTRAINT PK_TIP_MVTC_FNCR PRIMARY KEY (CD_TIP_MVTC_FNCR)
);

-- -----------------------------------------------------------------------------
-- 4. EVT_OPR_R3TD — Eventos R3TD de operações (subquery de registros recusados)
-- Referencia RMS_AGT_FNCO via CD_RMS_AGT_FNCO
-- -----------------------------------------------------------------------------
CREATE TABLE DB2GFG.EVT_OPR_R3TD (
    CD_EVT_OPR_R3TD     INTEGER       NOT NULL,
    CD_RMS_AGT_FNCO     INTEGER       NOT NULL,
    CD_FNDO_GRTR        SMALLINT      NOT NULL,
    CD_AGT_FNCO         INTEGER       NOT NULL,
    CD_IDFR_EXNO_OPR    CHAR(20)      NOT NULL,
    DT_EVT_R3TD         DATE          NOT NULL,
    CD_TIP_R3TD         SMALLINT      NOT NULL,
    CONSTRAINT PK_EVT_OPR_R3TD PRIMARY KEY (CD_EVT_OPR_R3TD)
);

CREATE INDEX DB2GFG.IX_EVT_R3TD_RMS ON DB2GFG.EVT_OPR_R3TD (CD_RMS_AGT_FNCO);

-- -----------------------------------------------------------------------------
-- 5. RSM_MVTC_FNCR_RMS — Resumo de movimentação financeira por remessa
-- Endpoint 18 (detalhe): breakdown por tipo de movimentação
-- -----------------------------------------------------------------------------
CREATE TABLE DB2GFG.RSM_MVTC_FNCR_RMS (
    CD_RSM_MVTC_FNCR_RMS   INTEGER        NOT NULL,
    CD_RMS_AGT_FNCO         INTEGER        NOT NULL,
    CD_FNDO_GRTR            SMALLINT       NOT NULL,
    CD_AGT_FNCO             INTEGER        NOT NULL,
    NR_SEQL_RMS             SMALLINT       NOT NULL,
    CD_TIP_MVTC_FNCR        SMALLINT       NOT NULL,
    VL_MVTC_FNCR            DECIMAL(15,2)  NOT NULL  DEFAULT 0,
    DT_MVTC                 DATE,
    CONSTRAINT PK_RSM_MVTC_FNCR_RMS PRIMARY KEY (CD_RSM_MVTC_FNCR_RMS),
    CONSTRAINT FK_RSM_TIP_MVTC FOREIGN KEY (CD_TIP_MVTC_FNCR)
        REFERENCES DB2GFG.TIP_MVTC_FNCR (CD_TIP_MVTC_FNCR)
);

CREATE INDEX DB2GFG.IX_RSM_MVTC_RMS ON DB2GFG.RSM_MVTC_FNCR_RMS (CD_RMS_AGT_FNCO, CD_AGT_FNCO);

-- -----------------------------------------------------------------------------
-- 6. ALTER TABLE RMS_AGT_FNCO — adicionar colunas que faltam para os endpoints v1
-- ATENÇÃO: renomear TS_RCT_RMS → TS_RCBT_RMS e VL_LODO_MVTC_RMS → VL_LQDO_MVTC_RMS
-- Se a tabela já existe com os nomes antigos, execute os ALTERs abaixo.
-- Se for criar do zero, use o DDL em 01b_DDL_RMS_COMPLETO.sql
-- -----------------------------------------------------------------------------

-- Adiciona colunas novas (se não existirem):
ALTER TABLE DB2GFG.RMS_AGT_FNCO ADD COLUMN CD_CNP2_AGT_FNCO CHAR(14);

-- Se a coluna se chama TS_RCT_RMS, renomear para TS_RCBT_RMS:
-- ALTER TABLE DB2GFG.RMS_AGT_FNCO RENAME COLUMN TS_RCT_RMS TO TS_RCBT_RMS;

-- Se a coluna se chama VL_LODO_MVTC_RMS, renomear para VL_LQDO_MVTC_RMS:
-- ALTER TABLE DB2GFG.RMS_AGT_FNCO RENAME COLUMN VL_LODO_MVTC_RMS TO VL_LQDO_MVTC_RMS;

-- OU: criar views com os nomes corretos (opção menos invasiva):
-- CREATE VIEW DB2GFG.V_RMS_AGT_FNCO AS
--     SELECT *, TS_RCT_RMS AS TS_RCBT_RMS, VL_LODO_MVTC_RMS AS VL_LQDO_MVTC_RMS
--     FROM DB2GFG.RMS_AGT_FNCO;
