package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Programa de Crédito (DB2GFG.TIP_PGM_CRD).
 * Ex: PRONAMPE, FGI, FGO, etc.
 */
@Entity
@Table(name = "TIP_PGM_CRD", schema = "DB2GFG")
public class TipoProgramaCredito {

    @Id
    @Column(name = "CD_TIP_PGM_CRD")
    private short cdTipPgmCrd;

    @Column(name = "NM_TIP_PGM_CRD", nullable = false, length = 100)
    private String nmTipPgmCrd;

    protected TipoProgramaCredito() {}

    public TipoProgramaCredito(short cdTipPgmCrd, String nmTipPgmCrd) {
        this.cdTipPgmCrd = cdTipPgmCrd;
        this.nmTipPgmCrd = nmTipPgmCrd;
    }

    public short getCdTipPgmCrd()  { return cdTipPgmCrd; }
    public String getNmTipPgmCrd() { return nmTipPgmCrd; }
}
