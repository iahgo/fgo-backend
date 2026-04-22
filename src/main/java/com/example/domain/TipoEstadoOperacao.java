package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Estado da Operação (DB2GFG.TIP_EST_OPR).
 */
@Entity
@Table(name = "TIP_EST_OPR", schema = "DB2GFG")
public class TipoEstadoOperacao {

    @Id
    @Column(name = "CD_TIP_EST_OPR")
    private short cdTipEstOpr;

    @Column(name = "NM_TIP_EST_OPR", nullable = false, length = 100)
    private String nmTipEstOpr;

    protected TipoEstadoOperacao() {}

    public TipoEstadoOperacao(short cdTipEstOpr, String nmTipEstOpr) {
        this.cdTipEstOpr = cdTipEstOpr;
        this.nmTipEstOpr = nmTipEstOpr;
    }

    public short getCdTipEstOpr()  { return cdTipEstOpr; }
    public String getNmTipEstOpr() { return nmTipEstOpr; }
}
