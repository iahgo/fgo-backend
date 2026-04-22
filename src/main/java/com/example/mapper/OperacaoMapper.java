package com.example.mapper;

import com.example.domain.OperacaoAgregada;
import com.example.dto.OperacaoKpiDto;
import com.example.dto.OperacaoResumoDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Responsável por converter objetos de domínio em DTOs e vice-versa.
 *
 * Isola a lógica de conversão do service e do repository.
 * Se no futuro migrarmos para MapStruct, basta anotar esta interface
 * com @Mapper e remover os métodos manuais.
 *
 * Regras de conversão:
 *   - taxaInad = totalInad / totalAtivas (calculado aqui, não vem do DB2)
 *   - carregadoEm = LocalDateTime.now() no momento da conversão
 */
@ApplicationScoped
public class OperacaoMapper {

    /**
     * Converte um OperacaoAgregada (domínio/DB2) em OperacaoKpiDto (API).
     * Calcula a taxa de inadimplência evitando divisão por zero.
     */
    public OperacaoKpiDto toKpiDto(OperacaoAgregada domain) {
        double taxaInad = domain.getTotalAtivas() > 0
                ? BigDecimal.valueOf((double) domain.getTotalInad() / domain.getTotalAtivas())
                        .setScale(6, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        return new OperacaoKpiDto(
                domain.getPrograma(),
                domain.getTotalAtivas(),
                domain.getVlrCarteira(),
                domain.getTotalInad(),
                taxaInad,
                domain.getVlrAtraso(),
                domain.getVlrGarantia()
        );
    }

    /**
     * Monta o OperacaoResumoDto completo para um agente/mês.
     * Registra o momento de carregamento (carregadoEm = agora).
     */
    public OperacaoResumoDto toResumoDto(int codAgente, String mesAno,
                                          List<OperacaoAgregada> dados) {
        List<OperacaoKpiDto> kpis = dados.stream()
                .map(this::toKpiDto)
                .toList();

        return new OperacaoResumoDto(codAgente, mesAno, kpis, LocalDateTime.now());
    }
}
