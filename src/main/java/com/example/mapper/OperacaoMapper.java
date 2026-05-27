package com.example.mapper;

import com.example.domain.OperacaoAgregada;
import com.example.dto.OperacaoKpiDto;
import com.example.dto.OperacaoResumoDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class OperacaoMapper {

    public OperacaoKpiDto toKpiDto(OperacaoAgregada domain) {
        double taxaInadimplencia = domain.getTotalAtivas() > 0
                ? BigDecimal.valueOf((double) domain.getTotalInad() / domain.getTotalAtivas())
                        .setScale(6, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        return new OperacaoKpiDto(
                domain.getPrograma(), domain.getTotalAtivas(), domain.getVlrCarteira(),
                domain.getTotalInad(), taxaInadimplencia, domain.getVlrAtraso(), domain.getVlrGarantia()
        );
    }

    public OperacaoResumoDto toResumoDto(int codAgente, List<OperacaoAgregada> dados) {
        List<OperacaoKpiDto> kpis = dados.stream().map(this::toKpiDto).toList();
        return new OperacaoResumoDto(codAgente, kpis, LocalDateTime.now());
    }
}
