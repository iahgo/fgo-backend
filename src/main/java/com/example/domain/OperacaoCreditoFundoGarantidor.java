package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidade JPA: Operação de Crédito do Fundo Garantidor (DB2GFG.OPR_CRD_FNDO_GRTR).
 * Tabela principal do sistema — cada linha representa uma operação de crédito garantida.
 *
 * Campos nullable usam tipos boxed (Integer, Short, BigDecimal) vs primitivos.
 * FK para tabelas de domínio são mantidas como código (sem @ManyToOne) para
 * permitir queries de agregação via JPQL cross-join sem carregamento desnecessário.
 */
@Entity
@Table(name = "OPR_CRD_FNDO_GRTR", schema = "DB2GFG")
public class OperacaoCreditoFundoGarantidor {

    @Id
    @Column(name = "CD_OPR_CRD_FNDO")
    private int cdOprCrdFndo;

    @Column(name = "CD_FNDO_GRTR", nullable = false)
    private int cdFndoGrtr;

    @Column(name = "CD_AGT_FNCO", nullable = false)
    private int cdAgtFnco;

    @Column(name = "CD_IDFR_EXNO_OPR", nullable = false, columnDefinition = "CHAR(20)")
    private String cdIdfrExnoOpr;

    @Column(name = "CD_TIP_MDLD_CRD", nullable = false)
    private short cdTipMdldCrd;

    @Column(name = "CD_TIP_FNLD_CRD", nullable = false)
    private short cdTipFnldCrd;

    @Column(name = "CD_TIP_PSS", nullable = false)
    private short cdTipPss;

    @Column(name = "NR_AG_CTRT_OPR", nullable = false)
    private short nrAgCtrtOpr;

    @Column(name = "CD_IBGE_MUN_AG", nullable = false)
    private int cdIbgeMunAg;

    @Column(name = "CD_IDFR_SRF", nullable = false, precision = 14, scale = 0)
    private BigDecimal cdIdfrSrf;

    @Column(name = "CD_RADC_CNPJ")
    private Integer cdRadcCnpj;

    @Column(name = "CD_TIP_PBCO_ALVO", nullable = false)
    private short cdTipPbcoAlvo;

    @Column(name = "VL_FATM_BRTO_AAL", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlFatmBrtoAal;

    @Column(name = "VL_OPR_CRD", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlOprCrd;

    @Column(name = "PC_GRT_OPR_CRD", nullable = false, precision = 5, scale = 2)
    private BigDecimal pcGrtOprCrd;

    @Column(name = "CD_TIP_CND_ESPL", nullable = false)
    private short cdTipCndEspl;

    @Column(name = "DT_FRMZ_OPR", nullable = false)
    private LocalDate dtFrmzOpr;

    @Column(name = "DT_VNCT_OPR", nullable = false)
    private LocalDate dtVnctOpr;

    @Column(name = "VL_SDO_CPTL_NMLD", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlSdoCptlNmld;

    @Column(name = "VL_SDO_CPTL_ATR", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlSdoCptlAtr;

    @Column(name = "VL_SDO_ENCG_NMLD", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlSdoEncgNmld;

    @Column(name = "VL_SDO_ENCG_ATR", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlSdoEncgAtr;

    @Column(name = "CD_NVL_RSCO_OPR", nullable = false, columnDefinition = "CHAR(2)")
    private String cdNvlRscoOpr;

    @Column(name = "DT_SDO_OPR")
    private LocalDate dtSdoOpr;

    @Column(name = "VL_GRT_OPR_AJSD", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlGrtOprAjsd;

    @Column(name = "VL_TTL_LIBD_OPR", nullable = false, precision = 17, scale = 2)
    private BigDecimal vlTtlLibdOpr;

    @Column(name = "DT_PRMO_SDO_ATR")
    private LocalDate dtPrmoSdoAtr;

    @Column(name = "CD_TIP_CRNG_AMTZ", nullable = false)
    private short cdTipCrngAmtz;

    @Column(name = "CD_TIP_EST_OPR", nullable = false)
    private short cdTipEstOpr;

    @Column(name = "DT_DPC_EXNO_OPR")
    private LocalDate dtDpcExnoOpr;

    @Column(name = "IC_FTR_CMSS", precision = 8, scale = 5)
    private BigDecimal icFtrCmss;

    @Column(name = "CD_TIP_FON_RCS", nullable = false)
    private short cdTipFonRcs;

    @Column(name = "CD_TIP_PGM_CRD", nullable = false)
    private short cdTipPgmCrd;

    @Column(name = "CD_TIP_FRMZ", nullable = false)
    private short cdTipFrmz;

    @Column(name = "CD_ENDO_GRTR", nullable = false)
    private short cdEndoGrtr;

    @Column(name = "IN_FATM_VLDD_SRF", nullable = false, columnDefinition = "CHAR(1)")
    private String inFatmVlddSrf;

    @Column(name = "IN_OPR_CADD_BC", nullable = false, columnDefinition = "CHAR(1)")
    private String inOprCaddBc;

    @Column(name = "VL_SBS_CRD", precision = 17, scale = 2)
    private BigDecimal vlSbsCrd;

    @Column(name = "CD_SEXO_QLF_OPR")
    private Short cdSexoQlfOpr;

    @Column(name = "NR_CPF_QLF_OPR", precision = 11, scale = 0)
    private BigDecimal nrCpfQlfOpr;

    @Column(name = "IC_PDA_CRD", precision = 7, scale = 6)
    private BigDecimal icPdaCrd;

    @Column(name = "CD_TIP_RGAO")
    private Short cdTipRgao;

    @Column(name = "CD_BC_OPR_CRD", columnDefinition = "CHAR(67)")
    private String cdBcOprCrd;

    @Column(name = "NR_SEQL_CT_MVT", nullable = false)
    private int nrSeqlCtMvt;

    @Column(name = "NR_CPF_MTR", precision = 11, scale = 0)
    private BigDecimal nrCpfMtr;

    @Column(name = "CD_CNPJ_MTR", columnDefinition = "CHAR(14)")
    private String cdCnpjMtr;

    @Column(name = "CD_RADC_CNPJ_MTR", columnDefinition = "CHAR(8)")
    private String cdRadcCnpjMtr;

    protected OperacaoCreditoFundoGarantidor() {}

    public OperacaoCreditoFundoGarantidor(int cdOprCrdFndo, int cdFndoGrtr, int cdAgtFnco,
                                          String cdIdfrExnoOpr, short cdTipMdldCrd, short cdTipFnldCrd,
                                          short cdTipPss, short nrAgCtrtOpr, int cdIbgeMunAg,
                                          BigDecimal cdIdfrSrf, Integer cdRadcCnpj, short cdTipPbcoAlvo,
                                          BigDecimal vlFatmBrtoAal, BigDecimal vlOprCrd, BigDecimal pcGrtOprCrd,
                                          short cdTipCndEspl, LocalDate dtFrmzOpr, LocalDate dtVnctOpr,
                                          BigDecimal vlSdoCptlNmld, BigDecimal vlSdoCptlAtr,
                                          BigDecimal vlSdoEncgNmld, BigDecimal vlSdoEncgAtr,
                                          String cdNvlRscoOpr, LocalDate dtSdoOpr,
                                          BigDecimal vlGrtOprAjsd, BigDecimal vlTtlLibdOpr,
                                          LocalDate dtPrmoSdoAtr, short cdTipCrngAmtz, short cdTipEstOpr,
                                          LocalDate dtDpcExnoOpr, BigDecimal icFtrCmss,
                                          short cdTipFonRcs, short cdTipPgmCrd, short cdTipFrmz,
                                          short cdEndoGrtr, String inFatmVlddSrf, String inOprCaddBc,
                                          BigDecimal vlSbsCrd, Short cdSexoQlfOpr, BigDecimal nrCpfQlfOpr,
                                          BigDecimal icPdaCrd, Short cdTipRgao, String cdBcOprCrd,
                                          int nrSeqlCtMvt, BigDecimal nrCpfMtr,
                                          String cdCnpjMtr, String cdRadcCnpjMtr) {
        this.cdOprCrdFndo  = cdOprCrdFndo;
        this.cdFndoGrtr    = cdFndoGrtr;
        this.cdAgtFnco     = cdAgtFnco;
        this.cdIdfrExnoOpr = cdIdfrExnoOpr;
        this.cdTipMdldCrd  = cdTipMdldCrd;
        this.cdTipFnldCrd  = cdTipFnldCrd;
        this.cdTipPss      = cdTipPss;
        this.nrAgCtrtOpr   = nrAgCtrtOpr;
        this.cdIbgeMunAg   = cdIbgeMunAg;
        this.cdIdfrSrf     = cdIdfrSrf;
        this.cdRadcCnpj    = cdRadcCnpj;
        this.cdTipPbcoAlvo = cdTipPbcoAlvo;
        this.vlFatmBrtoAal = vlFatmBrtoAal;
        this.vlOprCrd      = vlOprCrd;
        this.pcGrtOprCrd   = pcGrtOprCrd;
        this.cdTipCndEspl  = cdTipCndEspl;
        this.dtFrmzOpr     = dtFrmzOpr;
        this.dtVnctOpr     = dtVnctOpr;
        this.vlSdoCptlNmld = vlSdoCptlNmld;
        this.vlSdoCptlAtr  = vlSdoCptlAtr;
        this.vlSdoEncgNmld = vlSdoEncgNmld;
        this.vlSdoEncgAtr  = vlSdoEncgAtr;
        this.cdNvlRscoOpr  = cdNvlRscoOpr;
        this.dtSdoOpr      = dtSdoOpr;
        this.vlGrtOprAjsd  = vlGrtOprAjsd;
        this.vlTtlLibdOpr  = vlTtlLibdOpr;
        this.dtPrmoSdoAtr  = dtPrmoSdoAtr;
        this.cdTipCrngAmtz = cdTipCrngAmtz;
        this.cdTipEstOpr   = cdTipEstOpr;
        this.dtDpcExnoOpr  = dtDpcExnoOpr;
        this.icFtrCmss     = icFtrCmss;
        this.cdTipFonRcs   = cdTipFonRcs;
        this.cdTipPgmCrd   = cdTipPgmCrd;
        this.cdTipFrmz     = cdTipFrmz;
        this.cdEndoGrtr    = cdEndoGrtr;
        this.inFatmVlddSrf = inFatmVlddSrf;
        this.inOprCaddBc   = inOprCaddBc;
        this.vlSbsCrd      = vlSbsCrd;
        this.cdSexoQlfOpr  = cdSexoQlfOpr;
        this.nrCpfQlfOpr   = nrCpfQlfOpr;
        this.icPdaCrd      = icPdaCrd;
        this.cdTipRgao     = cdTipRgao;
        this.cdBcOprCrd    = cdBcOprCrd;
        this.nrSeqlCtMvt   = nrSeqlCtMvt;
        this.nrCpfMtr      = nrCpfMtr;
        this.cdCnpjMtr     = cdCnpjMtr;
        this.cdRadcCnpjMtr = cdRadcCnpjMtr;
    }

    public int getCdOprCrdFndo()          { return cdOprCrdFndo; }
    public int getCdFndoGrtr()            { return cdFndoGrtr; }
    public int getCdAgtFnco()             { return cdAgtFnco; }
    public String getCdIdfrExnoOpr()      { return cdIdfrExnoOpr; }
    public short getCdTipMdldCrd()        { return cdTipMdldCrd; }
    public short getCdTipFnldCrd()        { return cdTipFnldCrd; }
    public short getCdTipPss()            { return cdTipPss; }
    public short getNrAgCtrtOpr()         { return nrAgCtrtOpr; }
    public int getCdIbgeMunAg()           { return cdIbgeMunAg; }
    public BigDecimal getCdIdfrSrf()      { return cdIdfrSrf; }
    public Integer getCdRadcCnpj()        { return cdRadcCnpj; }
    public short getCdTipPbcoAlvo()       { return cdTipPbcoAlvo; }
    public BigDecimal getVlFatmBrtoAal()  { return vlFatmBrtoAal; }
    public BigDecimal getVlOprCrd()       { return vlOprCrd; }
    public BigDecimal getPcGrtOprCrd()    { return pcGrtOprCrd; }
    public short getCdTipCndEspl()        { return cdTipCndEspl; }
    public LocalDate getDtFrmzOpr()       { return dtFrmzOpr; }
    public LocalDate getDtVnctOpr()       { return dtVnctOpr; }
    public BigDecimal getVlSdoCptlNmld()  { return vlSdoCptlNmld; }
    public BigDecimal getVlSdoCptlAtr()   { return vlSdoCptlAtr; }
    public BigDecimal getVlSdoEncgNmld()  { return vlSdoEncgNmld; }
    public BigDecimal getVlSdoEncgAtr()   { return vlSdoEncgAtr; }
    public String getCdNvlRscoOpr()       { return cdNvlRscoOpr; }
    public LocalDate getDtSdoOpr()        { return dtSdoOpr; }
    public BigDecimal getVlGrtOprAjsd()   { return vlGrtOprAjsd; }
    public BigDecimal getVlTtlLibdOpr()   { return vlTtlLibdOpr; }
    public LocalDate getDtPrmoSdoAtr()    { return dtPrmoSdoAtr; }
    public short getCdTipCrngAmtz()       { return cdTipCrngAmtz; }
    public short getCdTipEstOpr()         { return cdTipEstOpr; }
    public LocalDate getDtDpcExnoOpr()    { return dtDpcExnoOpr; }
    public BigDecimal getIcFtrCmss()      { return icFtrCmss; }
    public short getCdTipFonRcs()         { return cdTipFonRcs; }
    public short getCdTipPgmCrd()         { return cdTipPgmCrd; }
    public short getCdTipFrmz()           { return cdTipFrmz; }
    public short getCdEndoGrtr()          { return cdEndoGrtr; }
    public String getInFatmVlddSrf()      { return inFatmVlddSrf; }
    public String getInOprCaddBc()        { return inOprCaddBc; }
    public BigDecimal getVlSbsCrd()       { return vlSbsCrd; }
    public Short getCdSexoQlfOpr()        { return cdSexoQlfOpr; }
    public BigDecimal getNrCpfQlfOpr()    { return nrCpfQlfOpr; }
    public BigDecimal getIcPdaCrd()       { return icPdaCrd; }
    public Short getCdTipRgao()           { return cdTipRgao; }
    public String getCdBcOprCrd()         { return cdBcOprCrd; }
    public int getNrSeqlCtMvt()           { return nrSeqlCtMvt; }
    public BigDecimal getNrCpfMtr()       { return nrCpfMtr; }
    public String getCdCnpjMtr()          { return cdCnpjMtr; }
    public String getCdRadcCnpjMtr()      { return cdRadcCnpjMtr; }
}
