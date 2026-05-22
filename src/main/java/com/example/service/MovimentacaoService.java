package com.example.service;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.MovimentacaoDetalheDto;
import com.example.dto.listagem.MovimentacaoItemDto;
import com.example.repository.MovimentacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para listagem paginada de movimentações financeiras.
 *
 * Mapeamento das colunas retornadas pelo native SQL (buildListSql() de MovimentacaoRepository):
 *   [0]  B.NM_FNDO_GRTR          → nmFundo
 *   [1]  A.NR_SEQL_RMS           → nrSequencialRemessa
 *   [2]  CASE NM_TIP_NTZ_MVTC    → tipoMovFinanceira (texto resolvido pelo CASE)
 *   [3]  G.NM_TIP_EST_RMS        → situacaoMovFinanceira / situacaoRemessa
 *   [4]  F.TX_TIP_MTV_RJC_RMS    → (motivo rejeição — contexto, não exibido no DTO de lista)
 *   [5]  A.DT_PRCT_EVT           → dataProcRemessa
 *   [6]  A.DT_ATL_MNTR           → dataAtualMonetaria
 *   [7]  A.DT_MVTC_FNCR          → dataMovFinanceira
 *   [8]  A.QT_REG_RMS            → qtdeRegistrosRemessa
 *   [9]  A.VL_LQDO_MVTC_RMS      → valorLiqMovimentado
 *
 * Mapeamento DET_MVT_FNCR (buscarDetalhe):
 *   [0]  C.NM_TIP_MVTC_FNCR      → tipoMovimentacao
 *   [1]  A.VL_NMML_MVTC_FNCR     → valorNominal
 *   [2]  A.VL_ATL_MNTR_MVTC      → atualizacaoMonetaria
 *   [3]  A.VL_LQDO_MVTD          → valorLiquido
 *
 * TODO: substituir cdAgtFnco por contexto JWT quando a autenticação for implementada.
 */
@ApplicationScoped
public class MovimentacaoService {

    private static final Logger LOG = Logger.getLogger(MovimentacaoService.class);

    @Inject
    MovimentacaoRepository movimentacaoRepository;

    private static final List<FiltroItemDto> SITUACOES_REMESSA = List.of(
            new FiltroItemDto("1", "Recebida"),
            new FiltroItemDto("2", "Em Processamento"),
            new FiltroItemDto("3", "Processada"),
            new FiltroItemDto("4", "Rejeitada"),
            new FiltroItemDto("5", "Cancelada")
    );

    public List<FiltroItemDto> listarSituacoesRemessa() {
        return SITUACOES_REMESSA;
    }

    /**
     * Lista movimentações paginadas com filtros opcionais.
     */
    public PageDto<MovimentacaoItemDto> listar(int cdAgtFnco, int cdFundo, int situacaoRemessa,
                                               Short nrSequencial, int page, int size) {
        LOG.debugf("[SVC-MOVIM] listar agente=%d fundo=%d sitRms=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial, page, size);
        long total = movimentacaoRepository.contar(cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial);
        List<Object[]> rows = movimentacaoRepository.listar(cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial, page, size);
        return PageDto.of(mapRows(rows), page, size, total);
    }

    /**
     * Retorna o detalhe (breakdown por tipo de movimentação) de uma remessa.
     * Query DET_MVT_FNCR do BI (custo 255.37).
     *
     * @param cdAgtFnco    código do agente
     * @param cdRmsAgtFnco PK de RMS_AGT_FNCO (código da remessa)
     */
    public List<MovimentacaoDetalheDto> buscarDetalhe(int cdAgtFnco, int cdRmsAgtFnco) {
        LOG.debugf("[SVC-MOVIM] buscarDetalhe agente=%d remessa=%d", cdAgtFnco, cdRmsAgtFnco);
        List<Object[]> rows = movimentacaoRepository.buscarDetalhe(cdAgtFnco, cdRmsAgtFnco);
        List<MovimentacaoDetalheDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // [0]=NM_TIP_MVTC_FNCR, [1]=VL_NMML_MVTC_FNCR, [2]=VL_ATL_MNTR_MVTC, [3]=VL_LQDO_MVTD
            result.add(new MovimentacaoDetalheDto(
                    trimNull(row[0]),
                    toBigDecimal(row[1]),
                    toBigDecimal(row[2]),
                    toBigDecimal(row[3])
            ));
        }
        return result;
    }

    /**
     * Lista todas as movimentações (sem paginação) para exportação CSV.
     */
    public List<MovimentacaoItemDto> listarTodos(int cdAgtFnco, int cdFundo,
                                                  int situacaoRemessa, Short nrSequencial) {
        LOG.debugf("[SVC-MOVIM] listarTodos agente=%d fundo=%d sitRms=%d seq=%s",
                cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial);
        List<Object[]> rows = movimentacaoRepository.listarTodos(cdAgtFnco, cdFundo, situacaoRemessa, nrSequencial);
        return mapRows(rows);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private List<MovimentacaoItemDto> mapRows(List<Object[]> rows) {
        List<MovimentacaoItemDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // Colunas conforme buildListSql() de MovimentacaoRepository (MVTC_FNCR):
            // [0] NM_FNDO_GRTR, [1] NR_SEQL_RMS, [2] CASE NM_TIP_NTZ_MVTC,
            // [3] NM_TIP_EST_RMS, [4] TX_TIP_MTV_RJC_RMS (não no DTO),
            // [5] DT_PRCT_EVT, [6] DT_ATL_MNTR, [7] DT_MVTC_FNCR,
            // [8] QT_REG_RMS, [9] VL_LQDO_MVTC_RMS
            String nmFundo      = trimNull(row[0]);
            int nrSeql          = row[1] != null ? ((Number) row[1]).intValue() : 0;
            String tipoMovFin   = trimNull(row[2]);  // CASE já resolvido
            String situacao     = trimNull(row[3]);  // NM_TIP_EST_RMS já resolvido
            // row[4] = TX_TIP_MTV_RJC_RMS (motivo — não exposto no DTO de lista)
            LocalDate dtProc    = toLocalDate(row[5]);
            LocalDate dtAtl     = toLocalDate(row[6]);
            LocalDate dtMovFin  = toLocalDate(row[7]);
            Integer qtReg       = row[8] != null ? ((Number) row[8]).intValue() : null;
            BigDecimal vlLqdo   = toBigDecimal(row[9]);

            result.add(new MovimentacaoItemDto(
                    nmFundo, nrSeql,
                    tipoMovFin,
                    situacao,   // situacaoMovFinanceira
                    situacao,   // situacaoRemessa — mesma coluna do BI
                    dtProc, dtAtl, dtMovFin,
                    qtReg, vlLqdo
            ));
        }
        return result;
    }

    private String trimNull(Object obj) {
        if (obj == null) return null;
        return obj.toString().trim();
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(obj.toString().trim());
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof Date d) return d.toLocalDate();
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return null;
    }
}
