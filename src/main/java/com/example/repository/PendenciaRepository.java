package com.example.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
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
    AgroalDataSource dataSource;

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
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                 String tipoPendencia, int page, int size) {
        LOG.debugf("[PENDENCIA] agente=%d fundo=%d prog=%d tipo=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, page, size);

        String sql = buildSql(tipoPendencia) + " LIMIT ? OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma);
            ps.setInt(6, size);
            ps.setInt(7, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar pendências", e);
        }
    }

    /** Conta total de pendências para paginação. */
    public long contar(int cdAgtFnco, int cdFundo, int cdPrograma, String tipoPendencia) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2D4W.DETT_OPR_PND A "
            + "LEFT JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "LEFT JOIN DB2GFG.TIP_PGM_CRD C ON A.CD_TIP_PGM_CRD = C.CD_TIP_PGM_CRD "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = D.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.AGT_FNCO E ON D.CD_AGT_FNCO = E.CD_AGT_FNCO "
            + whereClause(tipoPendencia);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar pendências", e);
        }
    }

    /** Lista TODAS as pendências (sem paginação) para exportação CSV. */
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String tipoPendencia) {
        LOG.debugf("[PENDENCIA] exportar agente=%d fundo=%d prog=%d tipo=%s",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(buildSql(tipoPendencia))) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao exportar pendências", e);
        }
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

    private void setCommonParams(PreparedStatement ps, int cdAgtFnco, int cdFundo,
                                 int cdPrograma) throws SQLException {
        ps.setInt(1, cdAgtFnco);
        ps.setInt(2, cdFundo);
        ps.setInt(3, cdFundo);
        ps.setInt(4, cdPrograma);
        ps.setInt(5, cdPrograma);
    }

    private List<Object[]> toListOfArrays(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[cols];
            for (int i = 1; i <= cols; i++) {
                row[i - 1] = rs.getObject(i);
            }
            result.add(row);
        }
        return result;
    }
}
