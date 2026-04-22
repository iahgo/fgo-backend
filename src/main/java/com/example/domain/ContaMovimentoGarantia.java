package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Conta de Movimento de Garantia da Operação (DB2GFG.CT_MVT_GRT_OPR).
 */
@Entity
@Table(name = "CT_MVT_GRT_OPR", schema = "DB2GFG")
public class ContaMovimentoGarantia {

    @Id
    @Column(name = "NR_SEQL_CT_MVT")
    private int nrSeqlCtMvt;

    @Column(name = "NM_CT_MVT_GRT_OPR", nullable = false, length = 100)
    private String nmCtMvtGrtOpr;

    protected ContaMovimentoGarantia() {}

    public ContaMovimentoGarantia(int nrSeqlCtMvt, String nmCtMvtGrtOpr) {
        this.nrSeqlCtMvt   = nrSeqlCtMvt;
        this.nmCtMvtGrtOpr = nmCtMvtGrtOpr;
    }

    public int getNrSeqlCtMvt()      { return nrSeqlCtMvt; }
    public String getNmCtMvtGrtOpr() { return nmCtMvtGrtOpr; }
}
