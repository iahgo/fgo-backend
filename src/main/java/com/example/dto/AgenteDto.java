package com.example.dto;

/**
 * DTO de resposta para listagem de agentes financeiros.
 * Retornado pelo endpoint GET /api/agentes.
 */
public class AgenteDto {

    private int codAgente;
    private String nome;

    public AgenteDto() {}

    public AgenteDto(int codAgente, String nome) {
        this.codAgente = codAgente;
        this.nome      = nome;
    }

    public int getCodAgente()       { return codAgente; }
    public void setCodAgente(int v) { this.codAgente = v; }

    public String getNome()         { return nome; }
    public void setNome(String v)   { this.nome = v; }
}
