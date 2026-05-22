package com.example.service;

import com.example.dto.painel.FundoDto;
import com.example.dto.painel.InadimplenciaDto;
import com.example.dto.painel.InformacoesGeraisDto;
import com.example.dto.painel.IvhItemDto;
import com.example.dto.painel.IvhSerieDto;
import com.example.dto.painel.MovimentacaoSerieDto;
import com.example.dto.painel.PendenciasGrupoDto;
import com.example.dto.painel.PendenciasResumoDto;
import com.example.dto.painel.PeriodoIvhDto;
import com.example.dto.painel.PeriodoMovimentacaoDto;
import com.example.dto.painel.ProgramaDto;
import com.example.dto.painel.RemessasResumoDto;
import com.example.repository.PainelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço de painel do agente financeiro.
 *
 * Cache não aplicado aqui — o projeto já usa Redis como cache distribuído.
 * As queries de agregação são executadas diretamente no DB2 via PainelRepository.
 * Para cache de curta duração, use o RedisDataSource (padrão já existente no projeto).
 *
 * TODO: substituir parâmetro cdAgtFnco por contexto JWT quando a autenticação for implementada.
 */
@ApplicationScoped
public class PainelService {

    private static final Logger LOG = Logger.getLogger(PainelService.class);

    @Inject
    PainelRepository painelRepository;

    // =========================================================================
    // FUNDOS
    // =========================================================================

    public List<FundoDto> listarFundos(int cdAgtFnco) {
        LOG.debugf("[SERVICE] listarFundos agente=%d", cdAgtFnco);
        List<Object[]> rows = painelRepository.buscarFundosPorAgente(cdAgtFnco);
        List<FundoDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row[0] = cdFndoGrtr (Short/Integer), row[1] = nmFndoGrtr (String)
            int cdFundo = toInt(row[0]);
            String nmFundo = trimNull(row[1]);
            result.add(new FundoDto(cdFundo, nmFundo));
        }
        return result;
    }

    // =========================================================================
    // PROGRAMAS
    // =========================================================================

    public List<ProgramaDto> listarProgramas(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] listarProgramas agente=%d fundo=%d", cdAgtFnco, cdFundo);
        List<Object[]> rows = painelRepository.buscarProgramasPorAgente(cdAgtFnco, cdFundo);
        List<ProgramaDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row[0] = cdTipPgmCrd (Short), row[1] = nmTipPgmCrd (String), row[2] = cdFndoGrtr (int)
            int cdPrograma = toInt(row[0]);
            String nmPrograma = trimNull(row[1]);
            int cdFundoItem = toInt(row[2]);
            result.add(new ProgramaDto(cdPrograma, nmPrograma, cdFundoItem));
        }
        return result;
    }

    // =========================================================================
    // INFORMACOES GERAIS
    // =========================================================================

    public InformacoesGeraisDto buscarInformacoesGerais(int cdAgtFnco, int cdFundo, int cdPrograma) {
        LOG.debugf("[SERVICE] buscarInformacoesGerais agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);
        Object[] row = painelRepository.buscarInformacoesGerais(cdAgtFnco, cdFundo, cdPrograma);
        // row[0]=mutuarios, row[1]=operacoes, row[2]=saldoContratado, row[3]=ticket, row[4]=saldoCarteira
        long mutuarios = toLong(row[0]);
        long operacoes = toLong(row[1]);
        BigDecimal saldoContratado = toBigDecimal(row[2]);
        BigDecimal ticketMedio = toBigDecimal(row[3]);
        BigDecimal saldoCarteira = toBigDecimal(row[4]);
        return new InformacoesGeraisDto(mutuarios, operacoes, saldoContratado, ticketMedio, saldoCarteira);
    }

    // =========================================================================
    // INADIMPLENCIA
    // =========================================================================

    public InadimplenciaDto buscarInadimplencia(int cdAgtFnco, int cdFundo, int cdPrograma) {
        LOG.debugf("[SERVICE] buscarInadimplencia agente=%d fundo=%d prog=%d", cdAgtFnco, cdFundo, cdPrograma);
        Object[] row = painelRepository.buscarInadimplencia(cdAgtFnco, cdFundo, cdPrograma);
        // row[0]=saldoAtr, row[1]=saldoNmld, row[2]=totalOps, row[3]=opsComAtr
        BigDecimal saldoAtrasado = toBigDecimal(row[0]);
        BigDecimal saldoNmld     = toBigDecimal(row[1]);
        long totalOps            = toLong(row[2]);
        long opsComAtr           = toLong(row[3]);

        BigDecimal indSaldo = BigDecimal.ZERO;
        BigDecimal indOps   = BigDecimal.ZERO;

        if (saldoNmld != null && saldoNmld.compareTo(BigDecimal.ZERO) > 0) {
            indSaldo = saldoAtrasado.divide(saldoNmld, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
        }
        if (totalOps > 0) {
            indOps = new BigDecimal(opsComAtr)
                    .divide(new BigDecimal(totalOps), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
        }

        return new InadimplenciaDto(
                saldoAtrasado != null ? saldoAtrasado : BigDecimal.ZERO,
                indSaldo,
                indOps
        );
    }

    // =========================================================================
    // IVH POR PROGRAMA
    // =========================================================================

    public List<IvhItemDto> buscarIvh(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] buscarIvh agente=%d fundo=%d", cdAgtFnco, cdFundo);
        List<Object[]> rows = painelRepository.buscarIvhPorPrograma(cdAgtFnco, cdFundo);
        List<IvhItemDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // [0]=cdTipPgmCrd, [1]=nmTipPgmCrd(CASE), [2]=coberturaMedia,
            // [3]=vlHonrados,  [4]=vlRecuperados,       [5]=vlContratado
            String cdPrograma       = String.valueOf(toInt(row[0]));
            String nmPrograma       = trimNull(row[1]);
            BigDecimal cobertura    = toBigDecimal(row[2]);
            BigDecimal vlHonrados   = toBigDecimal(row[3]);
            BigDecimal vlRecuperados = toBigDecimal(row[4]);
            BigDecimal vlContratado = toBigDecimal(row[5]);
            // IVH = vlHonrados / vlContratado * 100
            BigDecimal ivh = BigDecimal.ZERO;
            if (vlContratado != null && vlContratado.compareTo(BigDecimal.ZERO) > 0
                    && vlHonrados != null) {
                ivh = vlHonrados.divide(vlContratado, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            }
            result.add(new IvhItemDto(
                    cdPrograma, nmPrograma,
                    cobertura != null ? cobertura : BigDecimal.ZERO,
                    vlHonrados != null ? vlHonrados : BigDecimal.ZERO,
                    vlRecuperados != null ? vlRecuperados : BigDecimal.ZERO,
                    vlContratado != null ? vlContratado : BigDecimal.ZERO,
                    ivh
            ));
        }
        return result;
    }

    // =========================================================================
    // IVH SERIE HISTORICA
    // =========================================================================

    public IvhSerieDto buscarIvhSerieHistorica(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] buscarIvhSerieHistorica agente=%d fundo=%d", cdAgtFnco, cdFundo);
        List<Object[]> rows = painelRepository.buscarIvhSerieHistorica(cdAgtFnco, cdFundo);
        List<PeriodoIvhDto> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row[0]=ano, row[1]=mes, row[2]=vlGrtSum, row[3]=vlOprSum
            int ano = toInt(row[0]);
            int mes = toInt(row[1]);
            BigDecimal vlGrt = toBigDecimal(row[2]);
            BigDecimal vlOpr = toBigDecimal(row[3]);
            BigDecimal ivh = BigDecimal.ZERO;
            if (vlOpr != null && vlOpr.compareTo(BigDecimal.ZERO) > 0 && vlGrt != null) {
                ivh = vlGrt.divide(vlOpr, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            }
            String periodo = String.format("%04d-%02d", ano, mes);
            series.add(new PeriodoIvhDto(periodo, ivh));
        }
        return new IvhSerieDto(series);
    }

    // =========================================================================
    // REMESSAS RESUMO
    // =========================================================================

    public RemessasResumoDto buscarRemessasResumo(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] buscarRemessasResumo agente=%d fundo=%d", cdAgtFnco, cdFundo);
        List<Object[]> rows = painelRepository.buscarRemessasResumoPorStatus(cdAgtFnco, cdFundo);
        // cdTipEstRms: 1=Recebida, 2=Em proc, 3=Processada/Concluida, 4=Rejeitada, 5=Cancelada
        Map<Short, Long> statusMap = new HashMap<>();
        for (Object[] row : rows) {
            short status = (Short) row[0];
            long qtd = toLong(row[1]);
            statusMap.put(status, qtd);
        }
        long total         = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long concluidas    = statusMap.getOrDefault((short) 3, 0L);
        long naoMoviment   = statusMap.getOrDefault((short) 1, 0L) + statusMap.getOrDefault((short) 2, 0L);
        return new RemessasResumoDto(total, naoMoviment, concluidas);
    }

    // =========================================================================
    // PENDENCIAS RESUMO
    // =========================================================================

    @SuppressWarnings("unchecked")
    public PendenciasResumoDto buscarPendenciasResumo(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] buscarPendenciasResumo agente=%d fundo=%d", cdAgtFnco, cdFundo);
        // Nova query retorna List<Object[]>{nmTipPncOprCrd (String), qtd (Long)}
        // agrupado por tipo de pendência da tabela DB2D4W.DETT_OPR_PND
        List<Object[]> rows = (List<Object[]>) painelRepository.buscarPendenciasAgregado(cdAgtFnco, cdFundo);
        List<PendenciasGrupoDto> grupos = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String tipo  = trimNull(row[0]);
            long   qtd   = toLong(row[1]);
            grupos.add(new PendenciasGrupoDto(
                    tipo != null ? tipo : "OUTROS",
                    tipo != null ? tipo : "Outros",
                    new BigDecimal(qtd)
            ));
        }
        return new PendenciasResumoDto(grupos);
    }

    // =========================================================================
    // MOVIMENTACAO FINANCEIRA SERIE HISTORICA
    // =========================================================================

    public MovimentacaoSerieDto buscarMovimentacaoSerie(int cdAgtFnco, int cdFundo) {
        LOG.debugf("[SERVICE] buscarMovimentacaoSerie agente=%d fundo=%d", cdAgtFnco, cdFundo);

        // carteiraAgente: movimentação financeira líquida das remessas por mês
        List<Object[]> rowsAgente = painelRepository.buscarMovimentacaoSerie(cdAgtFnco, cdFundo);
        Map<String, BigDecimal> mapaAgente = new HashMap<>();
        for (Object[] row : rowsAgente) {
            int ano = toInt(row[0]);
            int mes = toInt(row[1]);
            String periodo = String.format("%04d-%02d", ano, mes);
            mapaAgente.put(periodo, toBigDecimal(row[2]));
        }

        // carteiraFundo: saldo de carteira por mês
        List<Object[]> rowsFundo = painelRepository.buscarCarteiraFundoSerie(cdAgtFnco, cdFundo);
        Map<String, BigDecimal> mapaFundo = new HashMap<>();
        List<String> periodosOrdenados = new ArrayList<>();
        for (Object[] row : rowsFundo) {
            int ano = toInt(row[0]);
            int mes = toInt(row[1]);
            String periodo = String.format("%04d-%02d", ano, mes);
            mapaFundo.put(periodo, toBigDecimal(row[2]));
            if (!periodosOrdenados.contains(periodo)) {
                periodosOrdenados.add(periodo);
            }
        }
        // adicionar períodos do agente que não estão no fundo
        for (String p : mapaAgente.keySet()) {
            if (!periodosOrdenados.contains(p)) {
                periodosOrdenados.add(p);
            }
        }
        periodosOrdenados.sort(String::compareTo);

        List<PeriodoMovimentacaoDto> series = new ArrayList<>(periodosOrdenados.size());
        for (String periodo : periodosOrdenados) {
            BigDecimal carteiraAgente = mapaAgente.getOrDefault(periodo, BigDecimal.ZERO);
            BigDecimal carteiraFundo  = mapaFundo.getOrDefault(periodo, BigDecimal.ZERO);
            if (carteiraAgente == null) carteiraAgente = BigDecimal.ZERO;
            if (carteiraFundo  == null) carteiraFundo  = BigDecimal.ZERO;
            series.add(new PeriodoMovimentacaoDto(periodo, carteiraAgente, carteiraFundo));
        }

        return new MovimentacaoSerieDto(series);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.intValue();
        return Integer.parseInt(obj.toString().trim());
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString().trim());
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(obj.toString().trim());
    }

    private String trimNull(Object obj) {
        if (obj == null) return null;
        return obj.toString().trim();
    }
}
