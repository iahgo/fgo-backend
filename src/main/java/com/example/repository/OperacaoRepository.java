package com.example.repository;

import com.example.domain.OperacaoAgregada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OperacaoRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRepository.class);

    @Inject
    EntityManager em;

    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<OperacaoAgregada> buscarAgregadoPorAgente(int codAgente) {
        LOG.debugf("[REPOSITORY] buscarAgregadoPorAgente agente=%d", codAgente);

        String sql =
            "SELECT A.NM_ABVO_TIP_PGM, "
            + "       COUNT(*), "
            + "       SUM(A.VL_SDO_CPTL_NMLD), "
            + "       SUM(CASE WHEN A.VL_SDO_CPTL_ATR > 0 THEN 1 ELSE 0 END), "
            + "       SUM(A.VL_SDO_CPTL_ATR), "
            + "       SUM(A.VL_GRT_OPR_AJSD) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "WHERE A.DT_REF = (SELECT MAX(Y.DT_REF) FROM DB2D4W.CTRA_FNDO_GRTR Y) "
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "GROUP BY A.NM_ABVO_TIP_PGM "
            + "ORDER BY SUM(A.VL_SDO_CPTL_NMLD) DESC";

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, codAgente)
                .getResultList();

        List<OperacaoAgregada> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new OperacaoAgregada(
                    (String) row[0],
                    row[1] == null ? 0L : ((Number) row[1]).longValue(),
                    row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2],
                    row[3] == null ? 0L : ((Number) row[3]).longValue(),
                    row[4] == null ? BigDecimal.ZERO : (BigDecimal) row[4],
                    row[5] == null ? BigDecimal.ZERO : (BigDecimal) row[5]
            ));
        }
        return result;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    @SuppressWarnings("unchecked")
    public List<Integer> buscarAgentesAtivos() {
        String sql = "SELECT DISTINCT CD_AGT_FNCO FROM DB2GFG.AGT_FNCO ORDER BY CD_AGT_FNCO";

        List<Object> rows = em.createNativeQuery(sql).getResultList();
        List<Integer> agentes = new ArrayList<>();
        for (Object row : rows) {
            agentes.add(((Number) row).intValue());
        }
        LOG.debugf("[REPOSITORY] %d agentes encontrados", agentes.size());
        return agentes;
    }
}
