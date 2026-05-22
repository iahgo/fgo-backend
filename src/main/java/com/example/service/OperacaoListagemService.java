package com.example.service;

import com.example.dto.PageDto;
import com.example.dto.listagem.OperacaoItemDto;
import com.example.repository.OperacaoListagemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para listagem paginada de operações de crédito.
 *
 * TODO: substituir cdAgtFnco por contexto JWT quando a autenticação for implementada.
 */
@ApplicationScoped
public class OperacaoListagemService {

    private static final Logger LOG = Logger.getLogger(OperacaoListagemService.class);

    @Inject
    OperacaoListagemRepository operacaoListagemRepository;

    /**
     * Lista operações paginadas com filtros opcionais.
     *
     * @param cdAgtFnco  código do agente financeiro (obrigatório)
     * @param cdFundo    código do fundo (-1 = todos)
     * @param cdPrograma código do programa (-1 = todos)
     * @param nrContrato número/parte do contrato (null = todos)
     * @param page       página (0-based)
     * @param size       tamanho da página (10, 50 ou 100)
     */
    public PageDto<OperacaoItemDto> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                           String nrContrato, int page, int size) {
        LOG.debugf("[SVC-OPERACAO] listar agente=%d fundo=%d prog=%d cont=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato, page, size);

        long total = operacaoListagemRepository.contar(cdAgtFnco, cdFundo, cdPrograma, nrContrato);
        List<Object[]> rows = operacaoListagemRepository.listar(cdAgtFnco, cdFundo, cdPrograma, nrContrato, page, size);
        List<OperacaoItemDto> content = mapRows(rows);
        return PageDto.of(content, page, size, total);
    }

    /**
     * Lista todas as operações (sem paginação) para exportação CSV.
     */
    public List<OperacaoItemDto> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String nrContrato) {
        LOG.debugf("[SVC-OPERACAO] listarTodos agente=%d fundo=%d prog=%d cont=%s",
                cdAgtFnco, cdFundo, cdPrograma, nrContrato);
        List<Object[]> rows = operacaoListagemRepository.listarTodos(cdAgtFnco, cdFundo, cdPrograma, nrContrato);
        return mapRows(rows);
    }

    private List<OperacaoItemDto> mapRows(List<Object[]> rows) {
        List<OperacaoItemDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row[0]=nmFndoGrtr, row[1]=nmTipPgmCrd, row[2]=cdIdfrExnoOpr,
            // row[3]=nmTipPbcoAlvo, row[4]=nmTipEstOpr, row[5]=dtFrmzOpr,
            // row[6]=dtVnctOpr, row[7]=vlOprCrd, row[8]=vlTtlLibdOpr
            String nmFundo     = trimNull(row[0]);
            String nmPrograma  = trimNull(row[1]);
            String nrOperacao  = trimNull(row[2]);
            String publicoAlvo = trimNull(row[3]);
            String estadoOpr   = trimNull(row[4]);
            LocalDate dtFormal = (LocalDate) row[5];
            LocalDate dtVenct  = (LocalDate) row[6];
            BigDecimal vlOpr   = (BigDecimal) row[7];
            BigDecimal vlLib   = (BigDecimal) row[8];
            result.add(new OperacaoItemDto(nmFundo, nmPrograma, nrOperacao, publicoAlvo,
                    estadoOpr, dtFormal, dtVenct, vlOpr, vlLib));
        }
        return result;
    }

    private String trimNull(Object obj) {
        if (obj == null) return null;
        return obj.toString().trim();
    }
}
