package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade JPA: Fundo Garantidor (DB2GFG.FNDO_GRTR).
 * Representa o fundo que garante as operações de crédito.
 */
@Entity
@Table(name = "FNDO_GRTR", schema = "DB2GFG")
public class FundoGarantidor {

    @Id
    @Column(name = "CD_FNDO_GRTR")
    private short cdFndoGrtr;

    @Column(name = "SG_FNDO_GRTR", nullable = false, columnDefinition = "CHAR(10)")
    private String sgFndoGrtr;

    @Column(name = "NM_FNDO_GRTR", nullable = false, columnDefinition = "CHAR(60)")
    private String nmFndoGrtr;

    @Column(name = "NR_AG_CT_MVT_FNDO", nullable = false)
    private int nrAgCtMvtFndo;

    @Column(name = "NR_CT_MVT_FNDO", nullable = false, precision = 11, scale = 0)
    private BigDecimal nrCtMvtFndo;

    @Column(name = "CD_CIA_CTB", columnDefinition = "CHAR(3)")
    private String cdCiaCtb;

    @Column(name = "IDT_ULT_FCHT_BAL", nullable = false)
    private LocalDate idtUltFchtBal;

    @Column(name = "DT_INC_FNDO_GRTR", nullable = false)
    private LocalDate dtIncFndoGrtr;

    @Column(name = "DT_ECR_FNDO_GRTR", nullable = false)
    private LocalDate dtEcrFndoGrtr;

    @Column(name = "CD_TIP_EST_ENDO", nullable = false)
    private short cdTipEstEndo;

    @Column(name = "CD_USU_RSP_ATL_REG", nullable = false, columnDefinition = "CHAR(8)")
    private String cdUsuRspAtlReg;

    @Column(name = "CD_USU_RSP_VLDC", columnDefinition = "CHAR(8)")
    private String cdUsuRspVldc;

    @Column(name = "TS_ATL_REG", nullable = false)
    private LocalDateTime tsAtlReg;

    protected FundoGarantidor() {}

    public FundoGarantidor(short cdFndoGrtr, String sgFndoGrtr, String nmFndoGrtr,
                           int nrAgCtMvtFndo, BigDecimal nrCtMvtFndo, String cdCiaCtb,
                           LocalDate idtUltFchtBal, LocalDate dtIncFndoGrtr, LocalDate dtEcrFndoGrtr,
                           short cdTipEstEndo, String cdUsuRspAtlReg, String cdUsuRspVldc,
                           LocalDateTime tsAtlReg) {
        this.cdFndoGrtr     = cdFndoGrtr;
        this.sgFndoGrtr     = sgFndoGrtr;
        this.nmFndoGrtr     = nmFndoGrtr;
        this.nrAgCtMvtFndo  = nrAgCtMvtFndo;
        this.nrCtMvtFndo    = nrCtMvtFndo;
        this.cdCiaCtb       = cdCiaCtb;
        this.idtUltFchtBal  = idtUltFchtBal;
        this.dtIncFndoGrtr  = dtIncFndoGrtr;
        this.dtEcrFndoGrtr  = dtEcrFndoGrtr;
        this.cdTipEstEndo   = cdTipEstEndo;
        this.cdUsuRspAtlReg = cdUsuRspAtlReg;
        this.cdUsuRspVldc   = cdUsuRspVldc;
        this.tsAtlReg       = tsAtlReg;
    }

    public short getCdFndoGrtr()         { return cdFndoGrtr; }
    public String getSgFndoGrtr()        { return sgFndoGrtr; }
    public String getNmFndoGrtr()        { return nmFndoGrtr; }
    public int getNrAgCtMvtFndo()        { return nrAgCtMvtFndo; }
    public BigDecimal getNrCtMvtFndo()   { return nrCtMvtFndo; }
    public String getCdCiaCtb()          { return cdCiaCtb; }
    public LocalDate getIdtUltFchtBal()  { return idtUltFchtBal; }
    public LocalDate getDtIncFndoGrtr()  { return dtIncFndoGrtr; }
    public LocalDate getDtEcrFndoGrtr()  { return dtEcrFndoGrtr; }
    public short getCdTipEstEndo()       { return cdTipEstEndo; }
    public String getCdUsuRspAtlReg()    { return cdUsuRspAtlReg; }
    public String getCdUsuRspVldc()      { return cdUsuRspVldc; }
    public LocalDateTime getTsAtlReg()   { return tsAtlReg; }
}
