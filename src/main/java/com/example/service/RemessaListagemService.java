package com.example.service;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.RemessaItemDto;
import com.example.repository.RemessaListagemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para listagem paginada de remessas do agente.
 *
 * Mapeamento das colunas retornadas pelo native SQL (REMESSAS BI, custo 2223.41):
 *   [0]  ANO_MES (CAST DATE)          → referencia (formatada MM/yyyy)
 *   [1]  A.TS_RCBT_RMS                → dataHoraRecebimento
 *   [2]  A.CD_CNP2_AGT_FNCO           → (CNPJ — contexto, não exposto no DTO)
 *   [3]  D.NM_ABVD_AGT_FNCO           → agenteFinanceiro
 *   [4]  A.CD_FNDO_GRTR               → (código fundo — contexto)
 *   [5]  B.SG_FNDO_GRTR               → nmFundo (sigla)
 *   [6]  A.NR_SEQL_RMS                → nrSequencial
 *   [7]  A.CD_TIP_EST_RMS             → (código — contexto)
 *   [8]  E.NM_TIP_EST_RMS             → situacao (texto resolvido pelo JOIN)
 *   [9]  A.CD_MTV_RJC_RMS             → (código — contexto)
 *   [10] F.TX_TIP_MTV_RJC_RMS         → motivoRejeicao (pode ser null)
 *   [11] A.QT_REG_RMS                 → qtdeRegistros
 *   [12] QT_REG_ACT (CASE)            → registrosAceitos
 *   [13] QF_RMS_RJC (subquery)        → registrosRecusados
 *
 * TODO: substituir cdAgtFnco por contexto JWT quando a autenticação for implementada.
 */
@ApplicationScoped
public class RemessaListagemService {

    private static final Logger LOG = Logger.getLogger(RemessaListagemService.class);

    @Inject
    RemessaListagemRepository remessaListagemRepository;

    private static final DateTimeFormatter REF_FMT = DateTimeFormatter.ofPattern("MM/yyyy");

    private static final List<FiltroItemDto> SITUACOES = List.of(
            new FiltroItemDto("1", "Recebida"),
            new FiltroItemDto("2", "Em Processamento"),
            new FiltroItemDto("3", "Processada"),
            new FiltroItemDto("4", "Rejeitada"),
            new FiltroItemDto("5", "Cancelada")
    );

    private static final List<FiltroItemDto> MOTIVOS_REJEICAO = List.of(
            new FiltroItemDto("0", "Sem Rejeição"),
            new FiltroItemDto("1", "Conteúdo Inválido")
    );

    public List<FiltroItemDto> listarSituacoes() {
        return SITUACOES;
    }

    public List<FiltroItemDto> listarMotivosRejeicao() {
        return MOTIVOS_REJEICAO;
    }

    /**
     * Lista remessas paginadas com filtros opcionais.
     *
     * @param cdAgtFnco      código do agente (obrigatório)
     * @param cdFundo        código do fundo (-1 = todos)
     * @param situacao       cdTipEstRms (-1 = todas)
     * @param motivoRejeicao cdMtvRjcRms (-1 = todos)
     * @param nrSequencial   nrSeqlRms (null = todos)
     * @param page           página (0-based)
     * @param size           tamanho da página (10, 50 ou 100)
     */
    public PageDto<RemessaItemDto> listar(int cdAgtFnco, int cdFundo, int situacao,
                                          int motivoRejeicao, Short nrSequencial, int page, int size) {
        LOG.debugf("[SVC-REMESSA] listar agente=%d fundo=%d sit=%d motivo=%d seq=%s page=%d size=%d",
                cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial, page, size);
        long total = remessaListagemRepository.contar(cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial);
        List<Object[]> rows = remessaListagemRepository.listar(cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial, page, size);
        return PageDto.of(mapRows(rows), page, size, total);
    }

    /**
     * Lista todas as remessas (sem paginação) para exportação CSV.
     */
    public List<RemessaItemDto> listarTodos(int cdAgtFnco, int cdFundo, int situacao,
                                             int motivoRejeicao, Short nrSequencial) {
        LOG.debugf("[SVC-REMESSA] listarTodos agente=%d fundo=%d sit=%d motivo=%d seq=%s",
                cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial);
        List<Object[]> rows = remessaListagemRepository.listarTodos(cdAgtFnco, cdFundo, situacao, motivoRejeicao, nrSequencial);
        return mapRows(rows);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    private List<RemessaItemDto> mapRows(List<Object[]> rows) {
        List<RemessaItemDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // Colunas conforme buildSql() de RemessaListagemRepository (REMESSAS BI):
            // [0] ANO_MES (Date), [1] TS_RCBT_RMS, [2] CD_CNP2_AGT_FNCO,
            // [3] NM_ABVD_AGT_FNCO, [4] CD_FNDO_GRTR, [5] SG_FNDO_GRTR,
            // [6] NR_SEQL_RMS, [7] CD_TIP_EST_RMS, [8] NM_TIP_EST_RMS,
            // [9] CD_MTV_RJC_RMS, [10] TX_TIP_MTV_RJC_RMS,
            // [11] QT_REG_RMS, [12] QT_REG_ACT, [13] QF_RMS_RJC
            LocalDate anoMes        = toLocalDate(row[0]);
            LocalDateTime tsRct     = toLocalDateTime(row[1]);
            // row[2] = CD_CNP2_AGT_FNCO (não exposto no DTO)
            String nmAgente         = trimNull(row[3]);
            // row[4] = CD_FNDO_GRTR (não exposto no DTO)
            String sgFundo          = trimNull(row[5]);
            int nrSeql              = row[6] != null ? ((Number) row[6]).intValue() : 0;
            // row[7] = CD_TIP_EST_RMS (não exposto no DTO)
            String situacaoLabel    = trimNull(row[8]);   // NM_TIP_EST_RMS
            // row[9] = CD_MTV_RJC_RMS (não exposto no DTO)
            String motivoLabel      = trimNull(row[10]);  // TX_TIP_MTV_RJC_RMS (pode ser null)
            Integer qtReg           = row[11] != null ? ((Number) row[11]).intValue() : null;
            Integer aceitos         = row[12] != null ? ((Number) row[12]).intValue() : null;
            Integer recusados       = row[13] != null ? ((Number) row[13]).intValue() : null;

            String referencia = anoMes != null ? anoMes.format(REF_FMT) : "";

            result.add(new RemessaItemDto(
                    referencia, tsRct, nrSeql,
                    nmAgente, sgFundo,
                    situacaoLabel, motivoLabel,
                    qtReg, aceitos, recusados
            ));
        }
        return result;
    }

    private String trimNull(Object obj) {
        if (obj == null) return null;
        return obj.toString().trim();
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof Date d) return d.toLocalDate();
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return null;
    }

    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime ldt) return ldt;
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof Date d) return d.toLocalDate().atStartOfDay();
        return null;
    }
}
