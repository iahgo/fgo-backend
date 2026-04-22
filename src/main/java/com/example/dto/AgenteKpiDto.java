package com.example.dto;

import java.math.BigDecimal;

/**
 * KPIs de um agente financeiro na visão consolidada.
 * Parte da resposta de GET /api/operacoes/{mesAno}/consolidado.
 */
public class AgenteKpiDto {

    private int codAgente;
    private String nomeAgente;
    private long totalAtivas;
    private BigDecimal vlrCarteira;
    private BigDecimal vlrGarantia;
    private BigDecimal vlrAtraso;
    private long totalInad;
    private double taxaInad;

    public AgenteKpiDto() {}

    public AgenteKpiDto(int codAgente, String nomeAgente, long totalAtivas,
                        BigDecimal vlrCarteira, BigDecimal vlrGarantia, BigDecimal vlrAtraso,
                        long totalInad, double taxaInad) {
        this.codAgente   = codAgente;
        this.nomeAgente  = nomeAgente;
        this.totalAtivas = totalAtivas;
        this.vlrCarteira = vlrCarteira;
        this.vlrGarantia = vlrGarantia;
        this.vlrAtraso   = vlrAtraso;
        this.totalInad   = totalInad;
        this.taxaInad    = taxaInad;
    }

    public int getCodAgente()               { return codAgente; }
    public void setCodAgente(int v)         { this.codAgente = v; }

    public String getNomeAgente()           { return nomeAgente; }
    public void setNomeAgente(String v)     { this.nomeAgente = v; }

    public long getTotalAtivas()            { return totalAtivas; }
    public void setTotalAtivas(long v)      { this.totalAtivas = v; }

    public BigDecimal getVlrCarteira()             { return vlrCarteira; }
    public void setVlrCarteira(BigDecimal v)       { this.vlrCarteira = v; }

    public BigDecimal getVlrGarantia()             { return vlrGarantia; }
    public void setVlrGarantia(BigDecimal v)       { this.vlrGarantia = v; }

    public BigDecimal getVlrAtraso()               { return vlrAtraso; }
    public void setVlrAtraso(BigDecimal v)         { this.vlrAtraso = v; }

    public long getTotalInad()              { return totalInad; }
    public void setTotalInad(long v)        { this.totalInad = v; }

    public double getTaxaInad()             { return taxaInad; }
    public void setTaxaInad(double v)       { this.taxaInad = v; }
}
