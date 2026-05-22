-- =============================================================================
-- SEED — Tabelas de domínio/lookup
-- Painel Agentes FGO · 2026-05-22
-- Execute após os DDLs.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- TIP_EST_RMS — Situações de remessa
-- -----------------------------------------------------------------------------
DELETE FROM DB2GFG.TIP_EST_RMS;

INSERT INTO DB2GFG.TIP_EST_RMS (CD_TIP_EST_RMS, NM_TIP_EST_RMS) VALUES
    (1, 'Recebida'),
    (2, 'Em Processamento'),
    (3, 'Processada'),
    (4, 'Rejeitada'),
    (5, 'Cancelada'),
    (6, 'Concluída com Erros');

-- -----------------------------------------------------------------------------
-- TIP_MTV_RJC_RMS — Motivos de rejeição
-- -----------------------------------------------------------------------------
DELETE FROM DB2GFG.TIP_MTV_RJC_RMS;

INSERT INTO DB2GFG.TIP_MTV_RJC_RMS (CD_MTV_RJC_RMS, TX_TIP_MTV_RJC_RMS) VALUES
    (0, 'Sem Rejeição'),
    (1, 'Conteúdo Inválido'),
    (2, 'Arquivo Corrompido'),
    (3, 'Schema Inválido'),
    (4, 'Duplicidade'),
    (5, 'Agente sem Convênio');

-- -----------------------------------------------------------------------------
-- TIP_MVTC_FNCR — Tipos de movimentação financeira (detalhe de remessa)
-- -----------------------------------------------------------------------------
DELETE FROM DB2GFG.TIP_MVTC_FNCR;

INSERT INTO DB2GFG.TIP_MVTC_FNCR (CD_TIP_MVTC_FNCR, NM_TIP_MVTC_FNCR) VALUES
    (1,  'Honra de Garantia'),
    (2,  'Recuperação de Honra'),
    (3,  'Devolução de Honra'),
    (4,  'Devolução de Recuperação'),
    (5,  'Taxas Administrativas'),
    (6,  'Correção Monetária'),
    (7,  'Juros de Mora'),
    (8,  'Multa Contratual'),
    (9,  'Liquidação Antecipada'),
    (10, 'Atualização de Saldo');
