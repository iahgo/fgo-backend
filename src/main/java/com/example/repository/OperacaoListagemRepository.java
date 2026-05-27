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
 * Schema principal: DB2D4W (analítico) / DB2GFG (domínio)
 */
@ApplicationScoped
public class OperacaoListagemRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoListagemRepository.class);

    @Inject
    AgroalDataSource dataSource;

    /**
     * Lista operações paginadas com filtros opcionais.
     *
     * Colunas retornadas (índices do Object[]):
     *   [0]  D.NM_FNDO_GRTR             nmFundo
     *   [1]  CASE NM_ABVO_TIP_PGM       nmPrograma (com Pronampe Solidário)
     *   [2]  A.CD_IDFR_EXNO_OPR         nrOperacao
     *   [3]  A.NM_TIP_PBCO_ALVO         publicoAlvo
     *   [4]  A.NM_TIP_EST_OPR           estadoOperacao
     *   [5]  A.DT_FRMZ_OPR              dataFormal
     *   [6]  A.DT_VNCT_OPR              dataVencimento
     *   [7]  A.VL_OPR_CRD               valorOperacao
     *   [8]  A.VL_TTL_LIBD_OPR          valorLiberado
     */
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                 String nrContrato, int page, int size) {
        LOG.debugf("[OPERACAO-LIST] agente=%d fundo=%d prog=%d contrato=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato, page, size);

        String sql = buildSql() + " LIMIT ? OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma, nrContrato);
            ps.setInt(8, size);
            ps.setInt(9, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar operações", e);
        }
    }

    /** Conta o total de operações para paginação. */
    public long contar(int cdAgtFnco, int cdFundo, int cdPrograma, String nrContrato) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2D4W.CTRA_FNDO_GRTR A "
            + "LEFT JOIN DB2GFG.AGT_FNCO_FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "      AND A.CD_AGT_FNCO = B.CD_AGT_FNCO "
            + "LEFT JOIN DB2GFG.FNDO_GRTR D ON A.CD_FNDO_GRTR = D.CD_FNDO_GRTR "
            + whereClause();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma, nrContrato);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar operações", e);
        }
    }

    /** Lista TODAS as operações (sem paginação) para exportação CSV. */
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String nrContrato) {
        LOG.debugf("[OPERACAO-LIST] exportar agente=%d fundo=%d prog=%d contrato=%s",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(buildSql())) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdPrograma, nrContrato);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao exportar operações", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSql() {
        return "SELECT "
            + "       D.NM_FNDO_GRTR, "
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
            + "       A.CD_IDFR_EXNO_OPR, "
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
     * Parâmetros: cdAgtFnco(1), cdFundo(2,3), cdPrograma(4,5), nrContratoLike(6), nrContrato(7).
     */
    private String whereClause() {
        return "WHERE A.DT_REF = (SELECT MAX(Y.DT_REF) FROM DB2D4W.CTRA_FNDO_GRTR Y) "
            + "  AND A.CD_FNDO_GRTR <> 1 "
            + "  AND A.CD_AGT_FNCO = ? "
            + "  AND (? = -1 OR A.CD_FNDO_GRTR = ?) "
            + "  AND (? = -1 OR A.CD_TIP_PGM_CRD = ?) "
            + "  AND (? IS NULL OR A.CD_IDFR_EXNO_OPR LIKE ?) ";
    }

    private void setCommonParams(PreparedStatement ps, int cdAgtFnco, int cdFundo,
                                 int cdPrograma, String nrContrato) throws SQLException {
        ps.setInt(1, cdAgtFnco);
        ps.setInt(2, cdFundo);
        ps.setInt(3, cdFundo);
        ps.setInt(4, cdPrograma);
        ps.setInt(5, cdPrograma);
        ps.setObject(6, nrContrato != null ? "%" + nrContrato + "%" : null);
        ps.setObject(7, nrContrato);
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
