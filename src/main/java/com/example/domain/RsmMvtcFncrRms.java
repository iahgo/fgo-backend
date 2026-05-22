package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Entidade JPA: Resumo de Movimentação Financeira por Remessa (DB2GFG.RSM_MVTC_FNCR_RMS).
 *
 * Tabela identificada nas queries do BI (DET_MVT_FNCR e MOVT_FNCR).
 * Uma linha por (CD_RMS_AGT_FNCO, CD_TIP_MVTC_FNCR) — detalhamento financeiro
 * de cada remessa dividido por tipo de movimentação.
 *
 * Query original do BI (DET_MVT_FNCR):
 *   SELECT A.CD_RMS_AGT_FNCO, A.CD_TIP_MVTC_FNCR, C.NM_TIP_MVTC_FNCR,
 *          A.QT_MVTC_FNCR, A.VL_NMML_MVTC_FNCR, A.VL_ATL_MNTR_MVTC,
 *          A.VL_TRBT_NMML, A.VL_TRBT_ATL_MNTR, A.VL_LQDO_MVTD
 *   FROM DB2GFG.RSM_MVTC_FNCR_RMS A
 *   INNER JOIN DB2GFG.RMS_AGT_FNCO B ON A.CD_RMS_AGT_FNCO = B.CD_RMS_AGT_FNCO
 *   INNER JOIN DB2GFG.TIP_MVTC_FNCR C ON A.CD_TIP_MVTC_FNCR = C.CD_TIP_MVTC_FNCR
 *   WHERE B.CD_FNDO_GRTR <> 1
 */
@Entity
@Table(name = "RSM_MVTC_FNCR_RMS", schema = "DB2GFG")
@IdClass(RsmMvtcFncrRmsId.class)
public class RsmMvtcFncrRms {

    /** FK para RMS_AGT_FNCO (parte da PK composta). */
    @Id
    @Column(name = "CD_RMS_AGT_FNCO", nullable = false)
    private int cdRmsAgtFnco;

    /** FK para TIP_MVTC_FNCR (parte da PK composta). */
    @Id
    @Column(name = "CD_TIP_MVTC_FNCR", nullable = false)
    private short cdTipMvtcFncr;

    /** Quantidade de movimentações deste tipo nesta remessa. */
    @Column(name = "QT_MVTC_FNCR")
    private Integer qtMvtcFncr;

    /** Valor nominal da movimentação (antes de atualização monetária). */
    @Column(name = "VL_NMML_MVTC_FNCR", precision = 17, scale = 2)
    private BigDecimal vlNmmlMvtcFncr;

    /** Atualização monetária aplicada. */
    @Column(name = "VL_ATL_MNTR_MVTC", precision = 17, scale = 2)
    private BigDecimal vlAtlMntrMvtc;

    /** Tributo sobre valor nominal. */
    @Column(name = "VL_TRBT_NMML", precision = 17, scale = 2)
    private BigDecimal vlTrbtNmml;

    /** Tributo sobre atualização monetária. */
    @Column(name = "VL_TRBT_ATL_MNTR", precision = 17, scale = 2)
    private BigDecimal vlTrbtAtlMntr;

    /** Valor líquido movimentado (= nominal + atualização - tributos). */
    @Column(name = "VL_LQDO_MVTD", precision = 17, scale = 2)
    private BigDecimal vlLqdoMvtd;

    protected RsmMvtcFncrRms() {}

    public int getCdRmsAgtFnco()           { return cdRmsAgtFnco; }
    public short getCdTipMvtcFncr()        { return cdTipMvtcFncr; }
    public Integer getQtMvtcFncr()         { return qtMvtcFncr; }
    public BigDecimal getVlNmmlMvtcFncr()  { return vlNmmlMvtcFncr; }
    public BigDecimal getVlAtlMntrMvtc()   { return vlAtlMntrMvtc; }
    public BigDecimal getVlTrbtNmml()      { return vlTrbtNmml; }
    public BigDecimal getVlTrbtAtlMntr()   { return vlTrbtAtlMntr; }
    public BigDecimal getVlLqdoMvtd()      { return vlLqdoMvtd; }
}
