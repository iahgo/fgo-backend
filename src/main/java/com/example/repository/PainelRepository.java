package com.example.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório de queries de agregação para o painel do agente.
 *
 * Todas as queries de operações usam DB2D4W.CTRA_FNDO_GRTR (tabela analítica do DW)
 * com filtro DT_REF = MAX(DT_REF) para pegar o snapshot mais recente.
 *
 * Queries de remessas usam DB2GFG.RMS_AGT_FNCO.
 * Queries de pendências usam DB2D4W.DETT_OPR_PND (para resumo do painel).
 *
 * WHERE base de operações: A.CD_FNDO_GRTR <> 1 (exclui FGO Original — padrão do BI).
 *
 * Schema analítico: DB2D4W | Schema domínio: DB2GFG
 */
@ApplicationScoped
public class PainelRepository {

    private static final Logger LOG = Logger.getLogger(PainelRepository.class);

    // Subquery para DT_REF mais recente (reutilizada em todas as queries de CTRA_FNDO_GRTR)
    private static final String MAX_DT_REF =
            "(SELECT MAX(Y.DT_REF) FROM DB2D4W.CTRA_FNDO_GRTR Y)";

    @Inject
    EntityManager em;

    // =========================================================================
    // FUNDOS — fundos com operações para o agente
    // =========================================================================

    /**
     * Retorna os fundos garantidores com operações do agente (DT_REF mais recente).
     * Retorna Object[]{cdFndoGrtr (int), nmFndoGrtr (String)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarFundosPorAgente(int cdAgtFnco) {
        LOG.debugf("[PAINEL] buscarFundosPorAgente agente=%d", cdAgtFnco);

        String sql = "SELECT DISTINCT A.CD_FNDO_GRTR, D.NM_FNDO_GRTR "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "LEFT JOIN DB2GFG.FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "ORDER BY D.NM_FNDO_GRTR";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .getResultList();
    }

    // =========================================================================
    // PROGRAMAS — programas com operações para o agente/fundo
    // =========================================================================

    /**
     * Retorna os programas de crédito com operações do agente.
     * cdFundo = -1 indica "todos os fundos".
     * Retorna Object[]{cdTipPgmCrd (int), nmTipPgmCrd (String), cdFndoGrtr (int)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarProgramasPorAgente(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarProgramasPorAgente agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT DISTINCT A.CD_TIP_PGM_CRD, "
            // nome com lógica Pronampe Solidário (conforme BI)
            + "       CASE "
            + "           WHEN A.CD_FNDO_GRTR = 1 THEN 'FGO Original' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2023-11-03' AND '2023-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 1' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2024-05-29' AND '2024-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 2' "
            + "           ELSE A.NM_ABVO_TIP_PGM "
            + "       END AS NM_ABVO_TIP_PGM, "
            + "       A.CD_FNDO_GRTR "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "ORDER BY 2";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    // =========================================================================
    // INFORMACOES GERAIS — KPIs do painel
    // =========================================================================

    /**
     * KPIs gerais: mutuários distintos, operações, saldo contratado, ticket médio, saldo carteira.
     * Retorna Object[]{mutuarios (Long), operacoes (Long), vlOprCrdSum (BigDecimal),
     *                   vlOprCrdAvg (BigDecimal), vlSdoCptlNmldSum (BigDecimal)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public Object[] buscarInformacoesGerais(int cdAgtFnco, int cdFundo, int cdPrograma) {
        LOG.debugf("[PAINEL] buscarInformacoesGerais agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);

        String sql = "SELECT COUNT(DISTINCT A.CD_IDFR_SRF), "
            + "       COUNT(*), "
            + "       SUM(A.VL_OPR_CRD), "
            + "       AVG(A.VL_OPR_CRD), "
            + "       SUM(A.VL_SDO_CPTL_NMLD) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_PGM_CRD = ?)";

        return (Object[]) em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .getSingleResult();
    }

    // =========================================================================
    // INADIMPLENCIA
    // =========================================================================

    /**
     * Dados de inadimplência: saldo atrasado, saldo nominal, total ops, ops com atraso.
     * Retorna Object[]{saldoAtr (BigDecimal), saldoNmld (BigDecimal), totalOps (Long), opsComAtr (Long)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public Object[] buscarInadimplencia(int cdAgtFnco, int cdFundo, int cdPrograma) {
        LOG.debugf("[PAINEL] buscarInadimplencia agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);

        String sql = "SELECT SUM(A.VL_SDO_CPTL_ATR), "
            + "       SUM(A.VL_SDO_CPTL_NMLD), "
            + "       COUNT(*), "
            + "       SUM(CASE WHEN A.VL_SDO_CPTL_ATR > 0 THEN 1 ELSE 0 END) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_PGM_CRD = ?)";

        return (Object[]) em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .getSingleResult();
    }

    // =========================================================================
    // IVH POR PROGRAMA (query IVH do BI, custo 1.88)
    // =========================================================================

    /**
     * IVH por programa de crédito.
     *
     * Retorna Object[]{cdTipPgmCrd, nmTipPgmCrd, coberturaMedia, vlHonrados, vlRecuperados, vlContratado}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarIvhPorPrograma(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarIvhPorPrograma agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT A.CD_TIP_PGM_CRD, "
            + "       CASE "
            + "           WHEN A.CD_FNDO_GRTR = 1 THEN 'FGO Original' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2023-11-03' AND '2023-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 1' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2024-05-29' AND '2024-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 2' "
            + "           ELSE A.NM_ABVO_TIP_PGM "
            + "       END AS NM_ABVO_TIP_PGM, "
            + "       AVG(A.PC_GRT_OPR_CRD) AS COBERTURA_MEDIA, "
            + "       COALESCE(SUM(A.VL_MVTC_HNR_GRT), 0) "
            + "           - COALESCE(SUM(A.VL_MVTC_DVLC_HNRD), 0) AS VL_HNRD, "
            + "       COALESCE(SUM(A.VL_MVTC_RCPD_HNRD), 0) "
            + "           - COALESCE(SUM(A.VL_MVTC_DVLC_RCPD), 0) AS VL_RCPD, "
            + "       SUM(A.VL_GRT_OPR_AJSD) AS VL_CONTRATADO "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "GROUP BY A.CD_TIP_PGM_CRD, "
            + "       CASE "
            + "           WHEN A.CD_FNDO_GRTR = 1 THEN 'FGO Original' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2023-11-03' AND '2023-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 1' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 "
            + "                AND A.DT_FRMZ_OPR BETWEEN '2024-05-29' AND '2024-12-31' "
            + "                THEN 'PRONAMPE SOLIDARIO RS 2' "
            + "           ELSE A.NM_ABVO_TIP_PGM "
            + "       END "
            + "ORDER BY 2";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    // =========================================================================
    // IVH SERIE HISTORICA
    // =========================================================================

    /**
     * Série histórica do IVH agrupada por ano/mês de DT_FRMZ_OPR.
     * Retorna Object[]{ano (Integer), mes (Integer), vlGrtOprAjsdSum (BigDecimal), vlOprCrdSum (BigDecimal)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarIvhSerieHistorica(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarIvhSerieHistorica agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT YEAR(A.DT_FRMZ_OPR), "
            + "       MONTH(A.DT_FRMZ_OPR), "
            + "       SUM(A.VL_GRT_OPR_AJSD), "
            + "       SUM(A.VL_OPR_CRD) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND A.DT_FRMZ_OPR IS NOT NULL "
            + "GROUP BY YEAR(A.DT_FRMZ_OPR), MONTH(A.DT_FRMZ_OPR) "
            + "ORDER BY YEAR(A.DT_FRMZ_OPR) ASC, MONTH(A.DT_FRMZ_OPR) ASC";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    // =========================================================================
    // REMESSAS RESUMO (continua em DB2GFG.RMS_AGT_FNCO)
    // =========================================================================

    /**
     * Resumo de remessas por status para o donut do painel.
     * Retorna Object[]{cdTipEstRms (short), qtd (Long)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarRemessasResumoPorStatus(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarRemessasResumoPorStatus agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT A.CD_TIP_EST_RMS, COUNT(*) "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "WHERE A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "GROUP BY A.CD_TIP_EST_RMS "
            + "ORDER BY A.CD_TIP_EST_RMS";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    // =========================================================================
    // PENDENCIAS RESUMO (DB2D4W.DETT_OPR_PND)
    // =========================================================================

    /**
     * Dados de pendências agregados por tipo para o gráfico de barras do painel.
     * Retorna Object[]{nmTipPncOprCrd (String), qtd (Long)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarPendenciasAgregado(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarPendenciasAgregado agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT A.NM_TIP_PNC_OPR_CRD, COUNT(*) "
            + "FROM DB2D4W.DETT_OPR_PND A "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "WHERE A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "GROUP BY A.NM_TIP_PNC_OPR_CRD "
            + "ORDER BY COUNT(*) DESC";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    // =========================================================================
    // MOVIMENTACAO FINANCEIRA SERIE HISTORICA (DB2GFG.RMS_AGT_FNCO)
    // =========================================================================

    /**
     * Série histórica de movimentação financeira líquida por mês (crédito do agente).
     * Retorna Object[]{ano (Integer), mes (Integer), vlLqdoMvtcRmsSum (BigDecimal)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarMovimentacaoSerie(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarMovimentacaoSerie agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT YEAR(A.DT_MVTC_FNCR), "
            + "       MONTH(A.DT_MVTC_FNCR), "
            + "       SUM(A.VL_LQDO_MVTC_RMS) "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "WHERE A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND A.CD_TIP_NTZ_MVTC IN (1, 2) "
            + "  AND A.DT_MVTC_FNCR IS NOT NULL "
            + "GROUP BY YEAR(A.DT_MVTC_FNCR), MONTH(A.DT_MVTC_FNCR) "
            + "ORDER BY YEAR(A.DT_MVTC_FNCR) ASC, MONTH(A.DT_MVTC_FNCR) ASC";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }

    /**
     * Saldo de carteira total do fundo por mês (para a linha do fundo no gráfico).
     * Retorna Object[]{ano (Integer), mes (Integer), vlSdoCptlNmldSum (BigDecimal)}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarCarteiraFundoSerie(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[PAINEL] buscarCarteiraFundoSerie agente=%d fundo=%d", cdAgtFnco, cdFundo);

        String sql = "SELECT YEAR(A.DT_FRMZ_OPR), "
            + "       MONTH(A.DT_FRMZ_OPR), "
            + "       SUM(A.VL_SDO_CPTL_NMLD) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = " + MAX_DT_REF
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND A.DT_FRMZ_OPR IS NOT NULL "
            + "GROUP BY YEAR(A.DT_FRMZ_OPR), MONTH(A.DT_FRMZ_OPR) "
            + "ORDER BY YEAR(A.DT_FRMZ_OPR) ASC, MONTH(A.DT_FRMZ_OPR) ASC";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .getResultList();
    }
}
