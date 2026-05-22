package com.example.service;

import com.example.dto.FiltroItemDto;
import com.example.dto.PageDto;
import com.example.dto.listagem.PendenciaItemDto;
import com.example.repository.PendenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para listagem paginada de pendências derivadas das operações de crédito.
 *
 * Tipos de pendência disponíveis:
 *   SALDO_ATRASADO      — operações com capital em atraso (vlSdoCptlAtr > 0)
 *   ENCARGO_ATRASADO    — operações com encargos em atraso (vlSdoEncgAtr > 0)
 *   LIQUIDADA_SEM_HONRA — liquidadas mas com garantia ajustada (vlGrtOprAjsd > 0 e vlSdoCptlNmld > 0)
 *   OP_SALDO_ZERO       — saldo nominal zero mas capital em atraso
 *
 * TODO: substituir cdAgtFnco por contexto JWT quando a autenticação for implementada.
 */
@ApplicationScoped
public class PendenciaService {

    private static final Logger LOG = Logger.getLogger(PendenciaService.class);

    @Inject
    PendenciaRepository pendenciaRepository;

    // Filtros estáticos de tipo de pendência
    private static final List<FiltroItemDto> TIPOS_PENDENCIA = List.of(
            new FiltroItemDto("SALDO_ATRASADO",      "Saldo em Atraso"),
            new FiltroItemDto("ENCARGO_ATRASADO",    "Encargo em Atraso"),
            new FiltroItemDto("LIQUIDADA_SEM_HONRA", "Liquidada Sem Honra"),
            new FiltroItemDto("OP_SALDO_ZERO",       "Operação com Saldo Zero")
    );

    public List<FiltroItemDto> listarTiposPendencia() {
        return TIPOS_PENDENCIA;
    }

    /**
     * Lista pendências paginadas com filtros opcionais.
     *
     * @param cdAgtFnco    código do agente (obrigatório)
     * @param cdFundo      código do fundo (-1 = todos)
     * @param cdPrograma   código do programa (-1 = todos)
     * @param tipoPendencia tipo de pendência (null = todas)
     * @param page         página (0-based)
     * @param size         tamanho da página (10, 50 ou 100)
     */
    public PageDto<PendenciaItemDto> listar(int cdAgtFnco, int cdFundo, int cdPrograma,
                                             String tipoPendencia, int page, int size) {
        LOG.debugf("[SVC-PEND] listar agente=%d fundo=%d prog=%d tipo=%s page=%d size=%d",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, page, size);
        long total = pendenciaRepository.contar(cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);
        List<Object[]> rows = pendenciaRepository.listar(cdAgtFnco, cdFundo, cdPrograma, tipoPendencia, page, size);
        List<PendenciaItemDto> content = mapRows(rows);
        return PageDto.of(content, page, size, total);
    }

    /**
     * Lista todas as pendências (sem paginação) para exportação CSV.
     */
    public List<PendenciaItemDto> listarTodos(int cdAgtFnco, int cdFundo, int cdPrograma, String tipoPendencia) {
        LOG.debugf("[SVC-PEND] listarTodos agente=%d fundo=%d prog=%d tipo=%s",
                cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);
        List<Object[]> rows = pendenciaRepository.listarTodos(cdAgtFnco, cdFundo, cdPrograma, tipoPendencia);
        return mapRows(rows);
    }

    private List<PendenciaItemDto> mapRows(List<Object[]> rows) {
        List<PendenciaItemDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row[0]=nmFndoGrtr, row[1]=nmTipPgmCrd, row[2]=cdIdfrExnoOpr,
            // row[3]=nmTipEstOpr, row[4]=tipoPendencia (CASE), row[5]=dtFrmzOpr
            String nmFundo       = trimNull(row[0]);
            String nmPrograma    = trimNull(row[1]);
            String nrContrato    = trimNull(row[2]);
            String situacaoCont  = trimNull(row[3]);
            String tipoPend      = trimNull(row[4]);
            LocalDate dtInicio   = (LocalDate) row[5];

            result.add(new PendenciaItemDto(nmFundo, nmPrograma, nrContrato,
                    situacaoCont, tipoPend, dtInicio));
        }
        return result;
    }

    private String trimNull(Object obj) {
        if (obj == null) return null;
        return obj.toString().trim();
    }
}
