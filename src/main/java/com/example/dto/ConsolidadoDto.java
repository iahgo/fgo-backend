package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Visão consolidada de todos os agentes para um mês.
 * Retornado pelo endpoint GET /api/operacoes/{mesAno}/consolidado.
 *
 * Contém:
 *   - totais gerais (carteira, garantia, atraso, inadimplência)
 *   - breakdown por programa de crédito
 *   - breakdown por agente financeiro (ranking de carteira)
 *
 * Dados lidos diretamente do DB2 (não passa pelo Redis).
 * Destinado a uso administrativo/gestor — não exposto ao agente individual.
 */
public class ConsolidadoDto {

    private String mesAno;
    private long totalOperacoes;
    private BigDecimal vlrCarteira;
    private BigDecimal vlrGarantia;
    private BigDecimal vlrAtraso;
    private long totalInad;
    private double taxaInadGeral;
    private List<OperacaoKpiDto> porPrograma;
    private List<AgenteKpiDto> porAgente;
    private LocalDateTime geradoEm;

    public ConsolidadoDto() {}

    public ConsolidadoDto(String mesAno, long totalOperacoes, BigDecimal vlrCarteira,
                          BigDecimal vlrGarantia, BigDecimal vlrAtraso, long totalInad,
                          double taxaInadGeral, List<OperacaoKpiDto> porPrograma,
                          List<AgenteKpiDto> porAgente, LocalDateTime geradoEm) {
        this.mesAno         = mesAno;
        this.totalOperacoes = totalOperacoes;
        this.vlrCarteira    = vlrCarteira;
        this.vlrGarantia    = vlrGarantia;
        this.vlrAtraso      = vlrAtraso;
        this.totalInad      = totalInad;
        this.taxaInadGeral  = taxaInadGeral;
        this.porPrograma    = porPrograma;
        this.porAgente      = porAgente;
        this.geradoEm       = geradoEm;
    }

    public String getMesAno()                        { return mesAno; }
    public void setMesAno(String v)                  { this.mesAno = v; }

    public long getTotalOperacoes()                  { return totalOperacoes; }
    public void setTotalOperacoes(long v)            { this.totalOperacoes = v; }

    public BigDecimal getVlrCarteira()               { return vlrCarteira; }
    public void setVlrCarteira(BigDecimal v)         { this.vlrCarteira = v; }

    public BigDecimal getVlrGarantia()               { return vlrGarantia; }
    public void setVlrGarantia(BigDecimal v)         { this.vlrGarantia = v; }

    public BigDecimal getVlrAtraso()                 { return vlrAtraso; }
    public void setVlrAtraso(BigDecimal v)           { this.vlrAtraso = v; }

    public long getTotalInad()                       { return totalInad; }
    public void setTotalInad(long v)                 { this.totalInad = v; }

    public double getTaxaInadGeral()                 { return taxaInadGeral; }
    public void setTaxaInadGeral(double v)           { this.taxaInadGeral = v; }

    public List<OperacaoKpiDto> getPorPrograma()               { return porPrograma; }
    public void setPorPrograma(List<OperacaoKpiDto> v)         { this.porPrograma = v; }

    public List<AgenteKpiDto> getPorAgente()                   { return porAgente; }
    public void setPorAgente(List<AgenteKpiDto> v)             { this.porAgente = v; }

    public LocalDateTime getGeradoEm()               { return geradoEm; }
    public void setGeradoEm(LocalDateTime v)         { this.geradoEm = v; }
}
