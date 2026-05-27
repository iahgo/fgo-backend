package com.example.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório para listagem paginada de operações de crédito.
 *
 * Query baseada no BASE_ANL_OPR do BI (custo D$64: 261810.82):
 *
 *   FROM DB2D4W.CTRA_FNDO_GRTR A
 *   LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR
 *          AND A.CD_AGT_FNCO = B.CD_AGT_FNCO
 *   LEFT JOIN DB2GFG.AGT_FNCO C ON B.CD_AGT_FNCO = C.CD_AGT_FNCO
 *   LEFT JOIN DB2GFG.FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR
 *   WHERE A.DT_REF = (SELECT MAX(Y.DT_REF) FROM DB2D4W.CTRA_FNDO_GRTR Y)
 *     AND A.CD_FNDO_GRTR <> 1
 *
 * CTRA_FNDO_GRTR é a tabela analítica do Data Warehouse (DB2D4W) que consolida
 * as operações de crédito com campos denormalizados (NM_TIP_EST_OPR, NM_TIP_PBCO_ALVO, etc.).
 *
 * CASE para NM_ABVO_TIP_PGM inclui lógica Pronampe Solidário RS 1 e RS 2 (datas do BI).
 *
 * Schema principal: DB2D4W (analítico) / DB2GFG (domínio)
 */
@ApplicationScoped
public class OperacaoListagemRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoListagemRepository.class);

    @Inject
    EntityManager em;

    /**
     * Lista operações paginadas com filtros opcionais.
     *
     * Colunas retornadas (índices do Object[]):
     *   [0]  D.NM_FNDO_GRTR             nmFundo
     *   [1]  CASE NM_ABVO_TIP_PGM       nmPrograma (com Pronampe Solidário)
     *   [2]  A.CD_IDFR_EXNO_OPR         nrOperacao (referência externa do contrato)
     *   [3]  A.NM_TIP_PBCO_ALVO         publicoAlvo
     *   [4]  A.NM_TIP_EST_OPR           estadoOperacao
     *   [5]  A.DT_FRMZ_OPR              dataFormal
     *   [6]  A.DT_VNCT_OPR              dataVencimento
     *   [7]  A.VL_OPR_CRD               valorOperacao
     *   [8]  A.VL_TTL_LIBD_OPR          valorLiberado
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                 String nrContrato, int page, int size) {
        LOG.debugf("[OPERACAO-LIST] agente=%d fundo=%d prog=%d contrato=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato, page, size);

        Query q = em.createNativeQuery(buildSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .setParameter(6, nrContrato != null ? "%" + nrContrato + "%" : null)
                .setParameter(7, nrContrato)
                .setFirstResult(page * size)
                .setMaxResults(size);
        return q.getResultList();
    }

    /** Conta o total de operações para paginação. */
    @Transactional(Transactional.TxType.REQUIRED)
    public long contar(int cdAgtFnco, int cdFundo, int cdPrograma, String nrContrato) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = B.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + whereClause();

        Object result = em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .setParameter(6, nrContrato != null ? "%" + nrContrato + "%" : null)
                .setParameter(7, nrContrato)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    /** Lista TODAS as operações (sem paginação) para exportação CSV. */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String nrContrato) {
        LOG.debugf("[OPERACAO-LIST] exportar agente=%d fundo=%d prog=%d contrato=%s",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato);

        return em.createNativeQuery(buildSql())
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .setParameter(6, nrContrato != null ? "%" + nrContrato + "%" : null)
                .setParameter(7, nrContrato)
                .getResultList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSql() {
        return "SELECT "
            + "       D.NM_FNDO_GRTR, "
            // CASE para nome do programa com lógica Pronampe Solidário RS 1 e RS 2 (datas do BI)
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
            // referência externa da operação (número do contrato)
            + "       A.CD_IDFR_EXNO_OPR, "
            // público alvo e estado: campos denormalizados na CTRA_FNDO_GRTR
            + "       A.NM_TIP_PBCO_ALVO, "
            + "       A.NM_TIP_EST_OPR, "
            + "       A.DT_FRMZ_OPR, "
            + "       A.DT_VNCT_OPR, "
            + "       A.VL_OPR_CRD, "
            + "       A.VL_TTL_LIBD_OPR "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = B.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.AGT_FNCO C ON B.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + whereClause()
            + " ORDER BY A.DT_FRMZ_OPR DESC";
    }

    /**
     * WHERE comum para lista e contagem.
     * Parâmetros: cdAgtFnco(1), cdFundo(2,3), cdPrograma(4,5), nrContrato(6,7).
     *
     * DT_REF = MAX: filtra apenas a referência mais recente (snapshot mais atual).
     * CD_FNDO_GRTR <> 1: exclui FGO Original (padrão do BI).
     */
    private String whereClause() {
        return "WHERE A.DT_REF = (SELECT MAX(Y.DT_REF) FROM DB2D4W.CTRA_FNDO_GRTR Y) "
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_PGM_CRD = ?) "
            + "  AND (? IS NULL OR A.CD_IDFR_EXNO_OPR LIKE ?) ";
    }
}
