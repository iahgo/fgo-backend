-- =============================================================================
-- DDL — RMS_AGT_FNCO completo (versão BI-correta)
-- Use este arquivo se for criar a tabela do zero (ambiente de dev limpo).
-- Inclui todos os campos usados pelos endpoints v1 com nomes corretos do BI.
-- =============================================================================

-- DROP TABLE DB2GFG.RMS_AGT_FNCO;  -- descomente se precisar recriar

CREATE TABLE DB2GFG.RMS_AGT_FNCO (
    -- PK e FK
    CD_RMS_AGT_FNCO     INTEGER        NOT NULL,
    CD_FNDO_GRTR        SMALLINT       NOT NULL,
    CD_AGT_FNCO         INTEGER        NOT NULL,

    -- Identificação da remessa
    NR_SEQL_RMS         SMALLINT       NOT NULL,
    NM_DTST             CHAR(44),
    CD_CNP2_AGT_FNCO    CHAR(14),               -- CNPJ do agente (novo)

    -- Datas e timestamps
    DT_VRS_LAUT         DATE,
    TS_RCBT_RMS         TIMESTAMP,              -- RCBT = recebimento (nome correto do BI)
    DT_PRCT_EVT         DATE,
    DT_CTC_UTZD_PRCT    DATE,
    DT_ATL_MNTR         DATE,
    DT_MVTC_FNCR        DATE,

    -- Status e motivo
    CD_TIP_EST_RMS      SMALLINT,               -- FK → TIP_EST_RMS
    CD_MTV_RJC_RMS      SMALLINT  DEFAULT 0,    -- FK → TIP_MTV_RJC_RMS

    -- Tipo de natureza da movimentação (1=Crédito do Fundo, 2=Crédito do Agente)
    CD_TIP_NTZ_MVTC     SMALLINT,

    -- Valores
    VL_LQDO_MVTC_RMS    DECIMAL(15,2),          -- LQDO = líquido (nome correto do BI)

    -- Quantidades
    QT_REG_RMS          INTEGER,

    CONSTRAINT PK_RMS_AGT_FNCO PRIMARY KEY (CD_RMS_AGT_FNCO),
    CONSTRAINT FK_RMS_FNDO   FOREIGN KEY (CD_FNDO_GRTR) REFERENCES DB2GFG.FNDO_GRTR (CD_FNDO_GRTR),
    CONSTRAINT FK_RMS_AGENTE FOREIGN KEY (CD_AGT_FNCO)  REFERENCES DB2GFG.AGT_FNCO  (CD_AGT_FNCO),
    CONSTRAINT FK_RMS_EST    FOREIGN KEY (CD_TIP_EST_RMS) REFERENCES DB2GFG.TIP_EST_RMS (CD_TIP_EST_RMS)
);

CREATE INDEX DB2GFG.IX_RMS_AGENTE_FUNDO ON DB2GFG.RMS_AGT_FNCO (CD_AGT_FNCO, CD_FNDO_GRTR);
CREATE INDEX DB2GFG.IX_RMS_EST          ON DB2GFG.RMS_AGT_FNCO (CD_TIP_EST_RMS);
CREATE INDEX DB2GFG.IX_RMS_MVTC_FNCR   ON DB2GFG.RMS_AGT_FNCO (DT_MVTC_FNCR, CD_TIP_NTZ_MVTC);
