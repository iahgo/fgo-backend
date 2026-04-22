package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade JPA: Remessa do Agente Financeiro (DB2GFG.RMS_AGT_FNCO).
 *
 * Uma remessa representa o envio de um lote de operações pelo agente ao fundo garantidor.
 * Cada remessa pode conter vários registros de operações (QT_REG_RMS).
 *
 * Estados possíveis (CD_TIP_EST_RMS):
 *   1 = Recebida
 *   2 = Em processamento
 *   3 = Processada
 *   4 = Rejeitada
 *   5 = Cancelada
 */
@Entity
@Table(name = "RMS_AGT_FNCO", schema = "DB2GFG")
public class RemessaAgente {

    @Id
    @Column(name = "CD_RMS_AGT_FNCO")
    private int cdRmsAgtFnco;

    @Column(name = "CD_FNDO_GRTR", nullable = false)
    private short cdFndoGrtr;

    @Column(name = "CD_AGT_FNCO", nullable = false)
    private int cdAgtFnco;

    @Column(name = "NR_SEQL_RMS")
    private Short nrSeqlRms;

    /** Nome do arquivo/dataset enviado pelo agente (ex: "AGT001_20260401_RMS_OPR.txt"). */
    @Column(name = "NM_DTST", nullable = false, columnDefinition = "CHAR(44)")
    private String nmDtst;

    @Column(name = "DT_VRS_LAUT")
    private LocalDate dtVrsLaut;

    @Column(name = "TS_RCT_RMS", nullable = false)
    private LocalDateTime tsRctRms;

    @Column(name = "DT_PRCT_EVT")
    private LocalDate dtPrctEvt;

    @Column(name = "DT_CTC_UTZD_PRCT")
    private LocalDate dtCtcUtzd;

    @Column(name = "DT_ATL_MNTR")
    private LocalDate dtAtlMntr;

    @Column(name = "DT_MVTC_FNCR")
    private LocalDate dtMvtcFncr;

    /** Motivo de rejeição (0 = sem rejeição). */
    @Column(name = "CD_MTV_RJC_RMS", nullable = false)
    private short cdMtvRjcRms;

    @Column(name = "VL_LODO_MVTC_RMS", precision = 17, scale = 2)
    private BigDecimal vlLodoMvtcRms;

    @Column(name = "CD_TIP_NTZ_MVTC")
    private Short cdTipNtzMvtc;

    @Column(name = "CD_TIP_EST_RMS", nullable = false)
    private short cdTipEstRms;

    @Column(name = "QT_REG_RMS")
    private Integer qtRegRms;

    protected RemessaAgente() {}

    public RemessaAgente(int cdRmsAgtFnco, short cdFndoGrtr, int cdAgtFnco, Short nrSeqlRms,
                         String nmDtst, LocalDate dtVrsLaut, LocalDateTime tsRctRms,
                         LocalDate dtPrctEvt, LocalDate dtCtcUtzd, LocalDate dtAtlMntr,
                         LocalDate dtMvtcFncr, short cdMtvRjcRms, BigDecimal vlLodoMvtcRms,
                         Short cdTipNtzMvtc, short cdTipEstRms, Integer qtRegRms) {
        this.cdRmsAgtFnco  = cdRmsAgtFnco;
        this.cdFndoGrtr    = cdFndoGrtr;
        this.cdAgtFnco     = cdAgtFnco;
        this.nrSeqlRms     = nrSeqlRms;
        this.nmDtst        = nmDtst;
        this.dtVrsLaut     = dtVrsLaut;
        this.tsRctRms      = tsRctRms;
        this.dtPrctEvt     = dtPrctEvt;
        this.dtCtcUtzd     = dtCtcUtzd;
        this.dtAtlMntr     = dtAtlMntr;
        this.dtMvtcFncr    = dtMvtcFncr;
        this.cdMtvRjcRms   = cdMtvRjcRms;
        this.vlLodoMvtcRms = vlLodoMvtcRms;
        this.cdTipNtzMvtc  = cdTipNtzMvtc;
        this.cdTipEstRms   = cdTipEstRms;
        this.qtRegRms      = qtRegRms;
    }

    public int getCdRmsAgtFnco()          { return cdRmsAgtFnco; }
    public short getCdFndoGrtr()          { return cdFndoGrtr; }
    public int getCdAgtFnco()             { return cdAgtFnco; }
    public Short getNrSeqlRms()           { return nrSeqlRms; }
    public String getNmDtst()             { return nmDtst; }
    public LocalDate getDtVrsLaut()       { return dtVrsLaut; }
    public LocalDateTime getTsRctRms()    { return tsRctRms; }
    public LocalDate getDtPrctEvt()       { return dtPrctEvt; }
    public LocalDate getDtCtcUtzd()       { return dtCtcUtzd; }
    public LocalDate getDtAtlMntr()       { return dtAtlMntr; }
    public LocalDate getDtMvtcFncr()      { return dtMvtcFncr; }
    public short getCdMtvRjcRms()         { return cdMtvRjcRms; }
    public BigDecimal getVlLodoMvtcRms()  { return vlLodoMvtcRms; }
    public Short getCdTipNtzMvtc()        { return cdTipNtzMvtc; }
    public short getCdTipEstRms()         { return cdTipEstRms; }
    public Integer getQtRegRms()          { return qtRegRms; }
}
