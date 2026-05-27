package com.example.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório para listagem paginada de remessas do agente.
 *
 * Schema: DB2GFG
 */
@ApplicationScoped
public class RemessaListagemRepository {

    private static final Logger LOG = Logger.getLogger(RemessaListagemRepository.class);

    @Inject
    EntityManager em;

    /**
     * Lista remessas paginadas com filtros.
     *
     * Colunas retornadas (índices do Object[]):
     *   [0]  ANO_MES (CAST DATE)        referencia para formatação MM/yyyy
     *   [1]  A.TS_RCBT_RMS              dataHoraRecebimento
     *   [2]  A.CD_CNP2_AGT_FNCO         CNPJ do agente
     *   [3]  D.NM_ABVD_AGT_FNCO         agenteFinanceiro
     *   [4]  A.CD_FNDO_GRTR             código fundo
     *   [5]  B.SG_FNDO_GRTR             nmFundo (sigla)
     *   [6]  A.NR_SEQL_RMS              nrSequencial
     *   [7]  A.CD_TIP_EST_RMS           código situação
     *   [8]  E.NM_TIP_EST_RMS           situacao
     *   [9]  A.CD_MTV_RJC_RMS           código motivo rejeição
     *   [10] F.TX_TIP_MTV_RJC_RMS       motivoRejeicao (pode ser null)
     *   [11] A.QT_REG_RMS               qtdeRegistros
     *   [12] QT_REG_ACT (CASE)          registrosAceitos
     *   [13] QF_RMS_RJC (subquery)      registrosRecusados
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdTipEstRms,
                                 int cdMtvRjcRms, Short nrSequencial, int page, int size) {
        LOG.debugf("[REMESSA-LIST] agente=%d fundo=%d est=%d motivo=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdTipEstRms, cdMtvRjcRms, nrSequencial, page, size);

        return em.createNativeQuery(buildSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, cdMtvRjcRms)
                .setParameter(7, cdMtvRjcRms)
                .setParameter(8, nrSequencial)
                .setParameter(9, nrSequencial)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /** Conta remessas para paginação. */
    @Transactional(Transactional.TxType.REQUIRED)
    public long contar(int cdAgtFnco, int cdFundo, int cdTipEstRms,
                       int cdMtvRjcRms, Short nrSequencial) {
        String sql = "SELECT COUNT(*) FROM DB2GFG.RMS_AGT_FNCO A "
            + "INNER JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "INNER JOIN DB2GFG.AGT_FNCO_FNDO_GRTR C ON A.CD_FNDO_GRTR = C.CD_FNDO_GRTR "
            + "       AND A.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.AGT_FNCO D ON C.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "INNER JOIN DB2GFG.TIP_EST_RMS E ON A.CD_TIP_EST_RMS = E.CD_TIP_EST_RMS "
            + "LEFT  JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS "
            + whereClause();

        Object result = em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, cdMtvRjcRms)
                .setParameter(7, cdMtvRjcRms)
                .setParameter(8, nrSequencial)
                .setParameter(9, nrSequencial)
                .getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /** Lista todas as remessas sem paginação para CSV. */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdTipEstRms,
                                      int cdMtvRjcRms, Short nrSequencial) {
        LOG.debugf("[REMESSA-LIST] exportar agente=%d fundo=%d est=%d motivo=%d seq=%s",
                cdAgtFnco, cdFundo, cdTipEstRms, cdMtvRjcRms, nrSequencial);

        return em.createNativeQuery(buildSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdTipEstRms)
                .setParameter(5, cdTipEstRms)
                .setParameter(6, cdMtvRjcRms)
                .setParameter(7, cdMtvRjcRms)
                .setParameter(8, nrSequencial)
                .setParameter(9, nrSequencial)
                .getResultList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSql() {
        return "SELECT "
            + "       CAST(YEAR(A.TS_RCBT_RMS) || '-' || LPAD(MONTH(A.TS_RCBT_RMS), 2, '0') || '-01' AS DATE) AS ANO_MES, "
            + "       A.TS_RCBT_RMS, "
            + "       A.CD_CNP2_AGT_FNCO, "
            + "       D.NM_ABVD_AGT_FNCO AS NM_AGT_FNCO, "
            + "       A.CD_FNDO_GRTR, "
            + "       B.SG_FNDO_GRTR, "
            + "       A.NR_SEQL_RMS, "
            + "       A.CD_TIP_EST_RMS, "
            + "       E.NM_TIP_EST_RMS, "
            + "       A.CD_MTV_RJC_RMS, "
            + "       F.TX_TIP_MTV_RJC_RMS, "
            + "       A.QT_REG_RMS, "
            + "       CASE "
            + "           WHEN A.CD_TIP_EST_RMS IN (5, 6) "
            + "           THEN A.QT_REG_RMS - 2 - COALESCE( "
            + "               (SELECT COUNT(*) FROM DB2GFG.EVT_OPR_R3TD R "
            + "                WHERE R.CD_RMS_AGT_FNCO = A.CD_RMS_AGT_FNCO), 0) "
            + "           ELSE 0 "
            + "       END AS QT_REG_ACT, "
            + "       COALESCE( "
            + "           (SELECT COUNT(*) FROM DB2GFG.EVT_OPR_R3TD R "
            + "            WHERE R.CD_RMS_AGT_FNCO = A.CD_RMS_AGT_FNCO), 0) AS QF_RMS_RJC "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "INNER JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "INNER JOIN DB2GFG.AGT_FNCO_FNDO_GRTR C ON A.CD_FNDO_GRTR = C.CD_FNDO_GRTR "
            + "       AND A.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.AGT_FNCO D ON C.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "INNER JOIN DB2GFG.TIP_EST_RMS E ON A.CD_TIP_EST_RMS = E.CD_TIP_EST_RMS "
            + "LEFT  JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS "
            + whereClause()
            + " ORDER BY A.TS_RCBT_RMS DESC";
    }

    /**
     * WHERE comum para lista e contagem.
     * Parâmetros: cdAgtFnco(1), cdFundo(2,3), cdTipEstRms(4,5), cdMtvRjcRms(6,7), nrSequencial(8,9).
     */
    private String whereClause() {
        return "WHERE A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_EST_RMS = ?) "
            + "  AND (? = -1 OR A.CD_MTV_RJC_RMS = ?) "
            + "  AND (? IS NULL OR A.NR_SEQL_RMS = ?) ";
    }
}
