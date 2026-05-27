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
 * Repositório para movimentações financeiras — queries fiéis ao BI original.
 *
 * Schema: DB2GFG
 */
@ApplicationScoped
public class MovimentacaoRepository {

    private static final Logger LOG = Logger.getLogger(MovimentacaoRepository.class);

    @Inject
    AgroalDataSource dataSource;

    // ─────────────────────────────────────────────────────────────────────────
    // DETALHE DE UMA REMESSA — DET_MVT_FNCR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna o breakdown de movimentação de uma remessa por tipo de movimentação.
     *
     * Retorna Object[]{nmTipMvtcFncr, vlNmmlMvtcFncr, vlAtlMntrMvtc, vlLqdoMvtd}.
     */
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cdAgtFnco);
            ps.setInt(2, cdRmsAgtFnco);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar detalhe de movimentação", e);
        }
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
    public List<Object[]> listar(int cdAgtFnco, int cdFundo, int cdTipEstRms,
                                 Short nrSequencial, int page, int size) {
        LOG.debugf("[MOVIM-LIST] agente=%d fundo=%d est=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial, page, size);

        String sql = buildListSql() + " LIMIT ? OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial);
            ps.setInt(8, size);
            ps.setInt(9, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar movimentações", e);
        }
    }

    /** Conta movimentações para paginação. */
    public long contar(int cdAgtFnco, int cdFundo, int cdTipEstRms, Short nrSequencial) {
        String sql = "SELECT COUNT(*) "
            + "FROM DB2GFG.RMS_AGT_FNCO A "
            + "INNER JOIN DB2GFG.FNDO_GRTR B ON A.CD_FNDO_GRTR = B.CD_FNDO_GRTR "
            + "INNER JOIN DB2GFG.AGT_FNCO_FNDO_GRTR C ON A.CD_FNDO_GRTR = C.CD_FNDO_GRTR "
            + "       AND A.CD_AGT_FNCO = C.CD_AGT_FNCO "
            + "LEFT  JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS "
            + "INNER JOIN DB2GFG.TIP_EST_RMS G ON A.CD_TIP_EST_RMS = G.CD_TIP_EST_RMS "
            + whereClause();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar movimentações", e);
        }
    }

    /** Lista todas as movimentações (sem paginação) para export CSV. */
    public List<Object[]> listarTodos(int cdAgtFnco, int cdFundo, int cdTipEstRms, Short nrSequencial) {
        LOG.debugf("[MOVIM-LIST] exportar agente=%d fundo=%d est=%d seq=%s",
                cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(buildListSql())) {
            setCommonParams(ps, cdAgtFnco, cdFundo, cdTipEstRms, nrSequencial);
            try (ResultSet rs = ps.executeQuery()) {
                return toListOfArrays(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao exportar movimentações", e);
        }
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

    private void setCommonParams(PreparedStatement ps, int cdAgtFnco, int cdFundo,
                                 int cdTipEstRms, Short nrSequencial) throws SQLException {
        ps.setInt(1, cdAgtFnco);
        ps.setInt(2, cdFundo);
        ps.setInt(3, cdFundo);
        ps.setInt(4, cdTipEstRms);
        ps.setInt(5, cdTipEstRms);
        ps.setObject(6, nrSequencial);
        ps.setObject(7, nrSequencial);
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
