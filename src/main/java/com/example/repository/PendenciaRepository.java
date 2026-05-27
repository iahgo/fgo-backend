package com.example.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório para listagem paginada de pendências.
 *
 * Schema principal: DB2D4W (analítico) / DB2GFG (domínio)
 */
@ApplicationScoped
public class PendenciaRepository {

    private static final Logger LOG = Logger.getLogger(PendenciaRepository.class);

    @Inject
    EntityManager em;

    /**
     * Lista pendências paginadas com filtros opcionais.
     *
     * Colunas retornadas (índices do Object[]):
     *   [0]  B.NM_FNDO_GRTR (ou SG)   nmFundo
     *   [1]  CASE NM_ABVD_TIP_PGM     nmPrograma (com Pronampe Solidário)
     *   [2]  A.CD_IDFR_EXNO_OPR       nrContrato
     *   [3]  A.CD_TIP_EST_OPR         situacaoContrato
     *   [4]  A.NM_TIP_PNC_OPR_CRD     tipoPendencia
     *   [5]  A.DT_SNC_PHC             dataInicioPendencia
     */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                 String tipoPendencia, int page, int size) {
        LOG.debugf("[PENDENCIA] agente=%d fundo=%d prog=%d tipo=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, page, size);

        return em.createNativeQuery(buildSql(tipoPendencia))
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /** Conta total de pendências para paginação. */
    @Transactional(Transactional.TxType.REQUIRED)
    public long contar(int cdAgtFnco, int cdFundo, int cdPrograma, String tipoPendencia) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2D4W.DETT_OPR_PND A "
            + "LEFT JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "LEFT JOIN DB2GFG.TIP_PGM_CRD C ON A.CD_TIP_PGM_CRD = C.CD_TIP_PGM_CRD "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.AGT_FNCO E ON D.CD_AGT_FNCO = E.CD_AGT_FNCO "
            + whereClause(tipoPendencia);

        Object result = em.createNativeQuery(sql)
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /** Lista TODAS as pendências (sem paginação) para exportação CSV. */
    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String tipoPendencia) {
        LOG.debugf("[PENDENCIA] exportar agente=%d fundo=%d prog=%d tipo=%s",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);

        return em.createNativeQuery(buildSql(tipoPendencia))
                .setParameter(1, cdAgtFnco)
                .setParameter(2, cdFundo)
                .setParameter(3, cdFundo)
                .setParameter(4, cdPrograma)
                .setParameter(5, cdPrograma)
                .getResultList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSql(String tipoPendencia) {
        return "SELECT "
            + "       B.NM_FNDO_GRTR, "
            + "       CASE "
            + "           WHEN A.CD_FNDO_GRTR = 1 THEN 'FGO Original' "
            + "           WHEN A.CD_TIP_PGM_CRD = 42 THEN 'PRONAMPE SOLIDARIO RS' "
            + "           ELSE C.NM_ABVD_TIP_PGM "
            + "       END AS NM_ABVD_TIP_PGM, "
            + "       A.CD_IDFR_EXNO_OPR, "
            + "       A.CD_TIP_EST_OPR, "
            + "       A.NM_TIP_PNC_OPR_CRD, "
            + "       A.DT_SNC_PHC "
            + "FROM DB2D4W.DETT_OPR_PND A "
            + "LEFT JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "LEFT JOIN DB2GFG.TIP_PGM_CRD C ON A.CD_TIP_PGM_CRD = C.CD_TIP_PGM_CRD "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.AGT_FNCO E ON D.CD_AGT_FNCO = E.CD_AGT_FNCO "
            + whereClause(tipoPendencia)
            + " ORDER BY A.DT_SNC_PHC ASC";
    }

    /**
     * WHERE comum para lista e contagem.
     * Parâmetros posicionais: cdAgtFnco(1), cdFundo(2,3), cdPrograma(4,5).
     * Filtro tipoPendencia é embutido diretamente no SQL (escaped).
     */
    private String whereClause(String tipoPendencia) {
        String base = "WHERE A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_PGM_CRD = ?) ";

        if (tipoPendencia != null && !tipoPendencia.isBlank()) {
            base += "  AND A.NM_TIP_PNC_OPR_CRD = '" + tipoPendencia.replace("'", "''") + "' ";
        }
        return base;
    }
}
