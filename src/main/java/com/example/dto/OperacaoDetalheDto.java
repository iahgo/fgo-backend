package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Representa uma operação individual da tabela OPR_CRD_FNDO_GRTR. */
public class OperacaoDetalheDto {

    private int    id;
    private int    codAgente;
    private int    codFundo;
    private String idExterno;       // CD_IDFR_EXNO_OPR (identificador no banco)
    private String programa;        // cd_tip_pgm_crd (código do programa)
    private short  tipoPessoa;      // 1=PF, 2=PJ
    private BigDecimal cpfCnpj;
    private BigDecimal vlrOperacao;
    private BigDecimal vlrCarteira; // saldo nominal
    private BigDecimal vlrAtraso;
    private BigDecimal vlrGarantia;
    private LocalDate  dataFormalizacao;
    private LocalDate  dataVencimento;
    private short  estado;          // CD_TIP_EST_OPR

    public OperacaoDetalheDto() {}

    public int getId()                          { return id; }
    public void setId(int v)                    { this.id = v; }
    public int getCodAgente()                   { return codAgente; }
    public void setCodAgente(int v)             { this.codAgente = v; }
    public int getCodFundo()                    { return codFundo; }
    public void setCodFundo(int v)              { this.codFundo = v; }
    public String getIdExterno()                { return idExterno; }
    public void setIdExterno(String v)          { this.idExterno = v != null ? v.trim() : null; }
    public String getPrograma()                 { return programa; }
    public void setPrograma(String v)           { this.programa = v; }
    public short getTipoPessoa()                { return tipoPessoa; }
    public void setTipoPessoa(short v)          { this.tipoPessoa = v; }
    public BigDecimal getCpfCnpj()              { return cpfCnpj; }
    public void setCpfCnpj(BigDecimal v)        { this.cpfCnpj = v; }
    public BigDecimal getVlrOperacao()          { return vlrOperacao; }
    public void setVlrOperacao(BigDecimal v)    { this.vlrOperacao = v; }
    public BigDecimal getVlrCarteira()          { return vlrCarteira; }
    public void setVlrCarteira(BigDecimal v)    { this.vlrCarteira = v; }
    public BigDecimal getVlrAtraso()            { return vlrAtraso; }
    public void setVlrAtraso(BigDecimal v)      { this.vlrAtraso = v; }
    public BigDecimal getVlrGarantia()          { return vlrGarantia; }
    public void setVlrGarantia(BigDecimal v)    { this.vlrGarantia = v; }
    public LocalDate getDataFormalizacao()       { return dataFormalizacao; }
    public void setDataFormalizacao(LocalDate v) { this.dataFormalizacao = v; }
    public LocalDate getDataVencimento()         { return dataVencimento; }
    public void setDataVencimento(LocalDate v)   { this.dataVencimento = v; }
    public short getEstado()                    { return estado; }
    public void setEstado(short v)              { this.estado = v; }
}
