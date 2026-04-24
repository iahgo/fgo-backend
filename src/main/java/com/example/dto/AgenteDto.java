package com.example.dto;

/**
 * DTO de resposta para listagem de agentes financeiros.
 * Retornado pelo endpoint GET /api/agentes.
 */
public class AgenteDto {

    private int codAgente;
    private String nome;
    private long totalOperacoes;   // total de operações em OPR_CRD_FNDO_GRTR (0 se agente sem operações)

    public AgenteDto() {}

    public AgenteDto(int codAgente, String nome, long totalOperacoes) {
        this.codAgente       = codAgente;
        this.nome            = nome;
        this.totalOperacoes  = totalOperacoes;
    }

    public int getCodAgente()            { return codAgente; }
    public void setCodAgente(int v)      { this.codAgente = v; }

    public String getNome()              { return nome; }
    public void setNome(String v)        { this.nome = v; }

    public long getTotalOperacoes()      { return totalOperacoes; }
    public void setTotalOperacoes(long v){ this.totalOperacoes = v; }
}
