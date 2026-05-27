package com.example.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório para movimentações financeiras — queries fiéis ao BI original.
 *
 * Schema: DB2GFG
 */
@ApplicationScoped
public class MovimentacaoRepository {

    private static final Logger LOG = Logger.getLogger(MovimentacaoRepository.class);

    @Inject
    EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // DETALHE DE UMA REMESSA — DET_MVT_FNCR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna o breakdown de movimentação de uma remessa por tipo de movimentação.
     *
     * Retorna Object[]{nmTipMvtcFncr, vlNmmlMvtcFncr, vlAtlMntrMvtc, vlLqdoMvtd}.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> buscarDetalhe(int cdAgtFnco, int cdRmsAgtFnco) {
        LOG.debugf("[MOVIM-DET] agente=%d remessa=%d", cdAgtFnco, cdRmsAgtFnco);

        String sql =
            "SELECT C.NM_TIP_MVTC_FNCR, "
            + "       A.VL_NMML_MVTC_FNCR, "
            + "       A.VL_ATL_MNTR_MVTC, "
            + "       A.VL_LQDO_MVTD "
            + "FROM DB2GFG.RSM_MVTC_FNCR_RMS A "
            + "INNER JOIN DB2GFG.RMS_AGT_FNCO B "
            + "        ON A.CD_RMS_AGT_FNCO = B.CD_RMS_AGT_FNCO "
            + "INNER JOIN DB2GFG.TIP_MVTC_FNCR C "
            + "        ON A.CD_TIP_MVTC_FNCR = C.CD_TIP_MVTC_FNCR "
            + "WHERE B.CD_FNDO_GRTR <> 1 "
            + "  AND B.CD_AGT_FNCO = ? "
            + "  AND A.CD_RMS_AGT_FNCO = ? "
            + "ORDER BY C.NM_TIP_MVTC_FNCR";

        return em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdRmsAgtFnco)
                .getResultList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LISTA DE MOVIMENTAÇÕES — MOVT_FNCR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lista paginada de movimentações financeiras.
     *
     * Colunas retornadas (índices do Object[]):
     *   [0]  B.NM_FNDO_GRTR          nmFundo
     *   [1]  A.NR_SEQL_RMS           nrSequencialRemessa
     *   [2]  CASE NM_TIP_NTZ_MVTC    tipoMovFinanceira
     *   [3]  G.NM_TIP_EST_RMS        situacaoMovFinanceira
     *   [4]  F.TX_TIP_MTV_RJC_RMS    motivoRejeicao (pode ser null)
     *   [5]  A.DT_PRCT_EVT           dataProcRemessa
     *   [6]  A.DT_ATL_MNTR           dataAtualMonetaria
     *   [7]  A.DT_MVTC_FNCR          dataMovFinanceira
     *   [8]  A.QT_REG_RMS            qtdeRegistrosRemessa
     *   [9]  A.VL_LQDO_MVTC_RMS      valorLiqMovimentado
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdTipEstRms,
                                 Short nrSequencial, int page, int size) {
        LOG.debugf("[MOVIM-LIST] agente=%d fundo=%d est=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial, page, size);

        return em.createNativeQuery(buildListSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, nrSequencial)
                .setParameter(7, nrSequencial)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /** Conta movimentações para paginação. */
    @Transactional(Transactional.TxType.REQUIRED)
    public long contar(int cdAgtFnco, int cdFundo, int cdTipEstRms, Short nrSequencial) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "INNER JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "INNER JOIN DB2GFG.AGT_FNCO_FNDO_GRTR C ON A.CD_FNDO_GRTR = C.CD_FNDO_GRTR "
            + "       AND A.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS "
            + "INNER JOIN DB2GFG.TIP_EST_RMS G ON A.CD_TIP_EST_RMS = G.CD_TIP_EST_RMS "
            + whereClause();

        Object result = em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, nrSequencial)
                .setParameter(7, nrSequencial)
                .getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /** Lista todas as movimentações (sem paginação) para export CSV. */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdTipEstRms, Short nrSequencial) {
        LOG.debugf("[MOVIM-LIST] exportar agente=%d fundo=%d est=%d seq=%s",
                cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial);

        return em.createNativeQuery(buildListSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, nrSequencial)
                .setParameter(7, nrSequencial)
                .getResultList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildListSql() {
        return "SELECT B.NM_FNDO_GRTR, "
            + "       A.NR_SEQL_RMS, "
            + "       CASE A.CD_TIP_NTZ_MVTC "
            + "           WHEN 1 THEN 'A crédito do fundo' "
            + "           WHEN 2 THEN 'A crédito do agente' "
            + "           WHEN 3 THEN 'Sem movimentação' "
            + "           ELSE 'Natureza não cadastrada' "
            + "       END, "
            + "       G.NM_TIP_EST_RMS, "
            + "       F.TX_TIP_MTV_RJC_RMS, "
            + "       A.DT_PRCT_EVT, "
            + "       A.DT_ATL_MNTR, "
            + "       A.DT_MVTC_FNCR, "
            + "       A.QT_REG_RMS, "
            + "       A.VL_LQDO_MVTC_RMS "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "INNER JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "INNER JOIN DB2GFG.AGT_FNCO_FNDO_GRTR C ON A.CD_FNDO_GRTR = C.CD_FNDO_GRTR "
            + "       AND A.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.AGT_FNCO E ON C.CD_AGT_FNCO = E.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS "
            + "INNER JOIN DB2GFG.TIP_EST_RMS G ON A.CD_TIP_EST_RMS = G.CD_TIP_EST_RMS "
            + whereClause()
            + " ORDER BY A.DT_MVTC_FNCR DESC";
    }

    /**
     * WHERE comum para lista e contagem.
     * Parâmetros: cdAgtFnco(1), cdFundo(2,3), cdTipEstRms(4,5), nrSequencial(6,7).
     */
    private String whereClause() {
        return "WHERE A.CD_TIP_NTZ_MVTC IN (1, 2) "
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_EST_RMS = ?) "
            + "  AND (? IS NULL OR A.NR_SEQL_RMS = ?) ";
    }
}
