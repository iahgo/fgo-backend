package com.example.domain;

import java.math.BigDecimal;

/**
 * Resultado de uma query de agregação por agente financeiro.
 * Usado no endpoint de visão consolidada (admin) para mostrar KPIs de todos os agentes.
 */
public final class AgenteAgregado {

    private final int codAgente;
    private final String nomeAgente;
    private final long totalAtivas;
    private final BigDecimal vlrCarteira;
    private final long totalInad;
    private final BigDecimal vlrAtraso;
    private final BigDecimal vlrGarantia;

    public AgenteAgregado(int codAgente, String nomeAgente, long totalAtivas,
                          BigDecimal vlrCarteira, long totalInad,
                          BigDecimal vlrAtraso, BigDecimal vlrGarantia) {
        this.codAgente   = codAgente;
        this.nomeAgente  = nomeAgente;
        this.totalAtivas = totalAtivas;
        this.vlrCarteira = vlrCarteira;
        this.totalInad   = totalInad;
        this.vlrAtraso   = vlrAtraso;
        this.vlrGarantia = vlrGarantia;
    }

    public int getCodAgente()          { return codAgente; }
    public String getNomeAgente()      { return nomeAgente; }
    public long getTotalAtivas()       { return totalAtivas; }
    public BigDecimal getVlrCarteira() { return vlrCarteira; }
    public long getTotalInad()         { return totalInad; }
    public BigDecimal getVlrAtraso()   { return vlrAtraso; }
    public BigDecimal getVlrGarantia() { return vlrGarantia; }
}
