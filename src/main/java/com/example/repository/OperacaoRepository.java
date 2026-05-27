package com.example.repository;

import com.example.domain.OperacaoAgregada;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OperacaoRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRepository.class);

    @Inject
    AgroalDataSource dataSource;

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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, codAgente);
            try (ResultSet rs = ps.executeQuery()) {
                List<OperacaoAgregada> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new OperacaoAgregada(
                            rs.getString(1),
                            rs.getObject(2) == null ? 0L : ((Number) rs.getObject(2)).longValue(),
                            rs.getObject(3) == null ? BigDecimal.ZERO : rs.getBigDecimal(3),
                            rs.getObject(4) == null ? 0L : ((Number) rs.getObject(4)).longValue(),
                            rs.getObject(5) == null ? BigDecimal.ZERO : rs.getBigDecimal(5),
                            rs.getObject(6) == null ? BigDecimal.ZERO : rs.getBigDecimal(6)
                    ));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar agregado por agente", e);
        }
    }

    public List<Integer> buscarAgentesAtivos() {
        String sql = "SELECT DISTINCT CD_AGT_FNCO FROM DB2GFG.AGT_FNCO ORDER BY CD_AGT_FNCO";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Integer> agentes = new ArrayList<>();
            while (rs.next()) {
                agentes.add(rs.getInt(1));
            }
            LOG.debugf("[REPOSITORY] %d agentes encontrados", agentes.size());
            return agentes;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar agentes ativos", e);
        }
    }
}
