package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de resposta para remessas do agente.
 * Retornado pelo endpoint GET /api/remessas.
 *
 * Traduz os códigos internos em descrições legíveis:
 *   cdTipEstRms → statusDescricao (ex: "Processada")
 */
public class RemessaDto {

    private int id;
    private int codAgente;
    private short codFundo;
    private Short sequencial;
    private String nomeArquivo;
    private LocalDateTime recebidaEm;
    private LocalDate processadaEm;
    private LocalDate atualizadaEm;
    private String status;
    private Integer qtdRegistros;
    private BigDecimal vlrLote;

    public RemessaDto() {}

    public RemessaDto(int id, int codAgente, short codFundo, Short sequencial,
                      String nomeArquivo, LocalDateTime recebidaEm, LocalDate processadaEm,
                      LocalDate atualizadaEm, String status, Integer qtdRegistros, BigDecimal vlrLote) {
        this.id           = id;
        this.codAgente    = codAgente;
        this.codFundo     = codFundo;
        this.sequencial   = sequencial;
        this.nomeArquivo  = nomeArquivo;
        this.recebidaEm   = recebidaEm;
        this.processadaEm = processadaEm;
        this.atualizadaEm = atualizadaEm;
        this.status       = status;
        this.qtdRegistros = qtdRegistros;
        this.vlrLote      = vlrLote;
    }

    public int getId()                         { return id; }
    public void setId(int v)                   { this.id = v; }

    public int getCodAgente()                  { return codAgente; }
    public void setCodAgente(int v)            { this.codAgente = v; }

    public short getCodFundo()                 { return codFundo; }
    public void setCodFundo(short v)           { this.codFundo = v; }

    public Short getSequencial()               { return sequencial; }
    public void setSequencial(Short v)         { this.sequencial = v; }

    public String getNomeArquivo()             { return nomeArquivo; }
    public void setNomeArquivo(String v)       { this.nomeArquivo = v; }

    public LocalDateTime getRecebidaEm()       { return recebidaEm; }
    public void setRecebidaEm(LocalDateTime v) { this.recebidaEm = v; }

    public LocalDate getProcessadaEm()         { return processadaEm; }
    public void setProcessadaEm(LocalDate v)   { this.processadaEm = v; }

    public LocalDate getAtualizadaEm()         { return atualizadaEm; }
    public void setAtualizadaEm(LocalDate v)   { this.atualizadaEm = v; }

    public String getStatus()                  { return status; }
    public void setStatus(String v)            { this.status = v; }

    public Integer getQtdRegistros()           { return qtdRegistros; }
    public void setQtdRegistros(Integer v)     { this.qtdRegistros = v; }

    public BigDecimal getVlrLote()             { return vlrLote; }
    public void setVlrLote(BigDecimal v)       { this.vlrLote = v; }
}
