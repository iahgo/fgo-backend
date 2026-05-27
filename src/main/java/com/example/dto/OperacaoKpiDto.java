package com.example.dto;

import java.math.BigDecimal;

public class OperacaoKpiDto {

    private String nomePrograma;
    private long quantidadeAtivas;
    private BigDecimal saldoCarteira;
    private long quantidadeInadimplentes;
    private double taxaInadimplencia;
    private BigDecimal saldoAtraso;
    private BigDecimal valorGarantia;

    public OperacaoKpiDto() {}

    public OperacaoKpiDto(String nomePrograma, long quantidadeAtivas, BigDecimal saldoCarteira,
                          long quantidadeInadimplentes, double taxaInadimplencia,
                          BigDecimal saldoAtraso, BigDecimal valorGarantia) {
        this.nomePrograma           = nomePrograma;
        this.quantidadeAtivas       = quantidadeAtivas;
        this.saldoCarteira          = saldoCarteira;
        this.quantidadeInadimplentes = quantidadeInadimplentes;
        this.taxaInadimplencia      = taxaInadimplencia;
        this.saldoAtraso            = saldoAtraso;
        this.valorGarantia          = valorGarantia;
    }

    public String getNomePrograma()                  { return nomePrograma; }
    public void setNomePrograma(String v)            { this.nomePrograma = v; }

    public long getQuantidadeAtivas()                { return quantidadeAtivas; }
    public void setQuantidadeAtivas(long v)          { this.quantidadeAtivas = v; }

    public BigDecimal getSaldoCarteira()             { return saldoCarteira; }
    public void setSaldoCarteira(BigDecimal v)       { this.saldoCarteira = v; }

    public long getQuantidadeInadimplentes()         { return quantidadeInadimplentes; }
    public void setQuantidadeInadimplentes(long v)  { this.quantidadeInadimplentes = v; }

    public double getTaxaInadimplencia()             { return taxaInadimplencia; }
    public void setTaxaInadimplencia(double v)       { this.taxaInadimplencia = v; }

    public BigDecimal getSaldoAtraso()               { return saldoAtraso; }
    public void setSaldoAtraso(BigDecimal v)         { this.saldoAtraso = v; }

    public BigDecimal getValorGarantia()             { return valorGarantia; }
    public void setValorGarantia(BigDecimal v)       { this.valorGarantia = v; }
}
