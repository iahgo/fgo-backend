package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Entidade JPA: Associação Agente Financeiro × Fundo Garantidor (DB2GFG.AGT_FNCO_FNDO_GRTR).
 * Chave primária composta: (cdFndoGrtr, cdAgtFnco) — ver {@link AgenteFinanceiroFundoGarantidorId}.
 */
@Entity
@Table(name = "AGT_FNCO_FNDO_GRTR", schema = "DB2GFG")
public class AgenteFinanceiroFundoGarantidor {

    @EmbeddedId
    private AgenteFinanceiroFundoGarantidorId id;

    @Column(name = "CD_TIP_EST_AGT", nullable = false)
    private short cdTipEstAgt;

    @Column(name = "CD_TIP_ITCB_FNCO", nullable = false)
    private short cdTipItcbFnco;

    @Column(name = "CD_ISPB", columnDefinition = "CHAR(8)")
    private String cdIspb;

    @Column(name = "NR_CTR_ITCB_DADO")
    private Integer nrCtrItcbDado;

    @Column(name = "CD_CLI")
    private Integer cdCli;

    @Column(name = "CD_PRD")
    private Short cdPrd;

    @Column(name = "NR_CT_DEP_AGT", precision = 11, scale = 0)
    private BigDecimal nrCtDepAgt;

    @Column(name = "NR_AG_CT_DEP_AGT")
    private Integer nrAgCtDepAgt;

    @Column(name = "CD_USU_RSP_VLDC", columnDefinition = "CHAR(8)")
    private String cdUsuRspVldc;

    protected AgenteFinanceiroFundoGarantidor() {}

    public AgenteFinanceiroFundoGarantidor(AgenteFinanceiroFundoGarantidorId id,
                                           short cdTipEstAgt, short cdTipItcbFnco,
                                           String cdIspb, Integer nrCtrItcbDado,
                                           Integer cdCli, Short cdPrd,
                                           BigDecimal nrCtDepAgt, Integer nrAgCtDepAgt,
                                           String cdUsuRspVldc) {
        this.id             = id;
        this.cdTipEstAgt    = cdTipEstAgt;
        this.cdTipItcbFnco  = cdTipItcbFnco;
        this.cdIspb         = cdIspb;
        this.nrCtrItcbDado  = nrCtrItcbDado;
        this.cdCli          = cdCli;
        this.cdPrd          = cdPrd;
        this.nrCtDepAgt     = nrCtDepAgt;
        this.nrAgCtDepAgt   = nrAgCtDepAgt;
        this.cdUsuRspVldc   = cdUsuRspVldc;
    }

    public AgenteFinanceiroFundoGarantidorId getId() { return id; }
    public short getCdTipEstAgt()                    { return cdTipEstAgt; }
    public short getCdTipItcbFnco()                  { return cdTipItcbFnco; }
    public String getCdIspb()                        { return cdIspb; }
    public Integer getNrCtrItcbDado()                { return nrCtrItcbDado; }
    public Integer getCdCli()                        { return cdCli; }
    public Short getCdPrd()                          { return cdPrd; }
    public BigDecimal getNrCtDepAgt()                { return nrCtDepAgt; }
    public Integer getNrAgCtDepAgt()                 { return nrAgCtDepAgt; }
    public String getCdUsuRspVldc()                  { return cdUsuRspVldc; }
}
