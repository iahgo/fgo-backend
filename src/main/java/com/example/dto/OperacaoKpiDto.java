package com.example.dto;

import java.math.BigDecimal;

/**
 * DTO de KPI por programa de crédito — exposto na API REST.
 *
 * Separado do domain (OperacaoAgregada) porque:
 *   - Pode evoluir de forma independente (adicionar/remover campos sem afetar o domínio)
 *   - Contém campos calculados (taxaInad) que não vêm do DB2
 *   - É serializado para JSON no Redis e retornado na API
 *
 * Precisa de construtor padrão (Jackson) e getters/setters (deserialização do Redis).
 */
public class OperacaoKpiDto {

    /** Nome do programa de crédito (ex: "PRONAMPE", "FGI", "PEAC"). */
    private String programa;

    /** Quantidade de operações ativas no período. */
    private long totalAtivas;

    /** Valor total da carteira garantida em reais. */
    private BigDecimal vlrCarteira;

    /** Quantidade de operações com status inadimplente. */
    private long totalInad;

    /**
     * Taxa de inadimplência: totalInad / totalAtivas.
     * Calculada pelo mapper — não vem do DB2.
     * Exemplo: 0.0055 representa 0,55%.
     */
    private double taxaInad;

    public OperacaoKpiDto() {}

    public OperacaoKpiDto(String programa, long totalAtivas, BigDecimal vlrCarteira,
                          long totalInad, double taxaInad) {
        this.programa    = programa;
        this.totalAtivas = totalAtivas;
        this.vlrCarteira = vlrCarteira;
        this.totalInad   = totalInad;
        this.taxaInad    = taxaInad;
    }

    public String getPrograma()        { return programa; }
    public void setPrograma(String v)  { this.programa = v; }

    public long getTotalAtivas()       { return totalAtivas; }
    public void setTotalAtivas(long v) { this.totalAtivas = v; }

    public BigDecimal getVlrCarteira()        { return vlrCarteira; }
    public void setVlrCarteira(BigDecimal v)  { this.vlrCarteira = v; }

    public long getTotalInad()         { return totalInad; }
    public void setTotalInad(long v)   { this.totalInad = v; }

    public double getTaxaInad()        { return taxaInad; }
    public void setTaxaInad(double v)  { this.taxaInad = v; }
}
