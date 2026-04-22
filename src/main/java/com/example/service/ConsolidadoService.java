package com.example.service;

import com.example.domain.AgenteAgregado;
import com.example.domain.OperacaoAgregada;
import com.example.dto.AgenteKpiDto;
import com.example.dto.ConsolidadoDto;
import com.example.dto.OperacaoKpiDto;
import com.example.mapper.OperacaoMapper;
import com.example.repository.OperacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço de visão consolidada — agrega KPIs de todos os agentes via DB2.
 *
 * Destinado a uso administrativo (gestor/supervisor).
 * Executa duas queries pesadas no DB2 — não usa Redis.
 * Tempo esperado: 10–60s dependendo do volume de dados e do mês.
 */
@ApplicationScoped
public class ConsolidadoService {

    private static final Logger LOG = Logger.getLogger(ConsolidadoService.class);

    @Inject
    OperacaoRepository repository;

    @Inject
    OperacaoMapper mapper;

    /**
     * Monta a visão consolidada para um mês, consultando diretamente o DB2.
     *
     * @param mesAno formato YYYY-MM (ex: "2026-04")
     */
    public ConsolidadoDto getConsolidado(String mesAno) {
        String[] partes = mesAno.split("-");
        int ano = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);

        LOG.infof("[CONSOLIDADO] Consultando DB2 para mes=%s", mesAno);
        long inicio = System.currentTimeMillis();

        // Query 1: breakdown por programa
        List<OperacaoAgregada> porPrograma = repository.buscarConsolidadoPorPrograma(ano, mes);
        List<OperacaoKpiDto> kpisPorPrograma = porPrograma.stream()
                .map(mapper::toKpiDto)
                .toList();

        // Query 2: breakdown por agente
        List<AgenteAgregado> porAgente = repository.buscarConsolidadoPorAgente(ano, mes);
        List<AgenteKpiDto> kpisPorAgente = porAgente.stream()
                .map(this::toAgenteKpi)
                .toList();

        // Totais gerais (reduz sobre os programas)
        long totalOperacoes = porPrograma.stream().mapToLong(OperacaoAgregada::getTotalAtivas).sum();
        BigDecimal vlrCarteira = porPrograma.stream()
                .map(OperacaoAgregada::getVlrCarteira)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vlrGarantia = porPrograma.stream()
                .map(a -> a.getVlrGarantia() != null ? a.getVlrGarantia() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vlrAtraso = porPrograma.stream()
                .map(a -> a.getVlrAtraso() != null ? a.getVlrAtraso() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalInad = porPrograma.stream().mapToLong(OperacaoAgregada::getTotalInad).sum();
        double taxaGeral = totalOperacoes > 0
                ? BigDecimal.valueOf((double) totalInad / totalOperacoes)
                        .setScale(6, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        long ms = System.currentTimeMillis() - inicio;
        LOG.infof("[CONSOLIDADO] Concluído em %dms | %d programas | %d agentes | mes=%s",
                ms, kpisPorPrograma.size(), kpisPorAgente.size(), mesAno);

        return new ConsolidadoDto(mesAno, totalOperacoes, vlrCarteira, vlrGarantia, vlrAtraso,
                totalInad, taxaGeral, kpisPorPrograma, kpisPorAgente, LocalDateTime.now());
    }

    private AgenteKpiDto toAgenteKpi(AgenteAgregado a) {
        double taxaInad = a.getTotalAtivas() > 0
                ? BigDecimal.valueOf((double) a.getTotalInad() / a.getTotalAtivas())
                        .setScale(6, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        return new AgenteKpiDto(
                a.getCodAgente(),
                a.getNomeAgente() != null ? a.getNomeAgente().trim() : null,
                a.getTotalAtivas(),
                a.getVlrCarteira(),
                a.getVlrGarantia(),
                a.getVlrAtraso(),
                a.getTotalInad(),
                taxaInad
        );
    }
}
