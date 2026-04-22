package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Fonte de Recursos (DB2GFG.TIP_FON_RCS).
 */
@Entity
@Table(name = "TIP_FON_RCS", schema = "DB2GFG")
public class TipoFonteRecursos {

    @Id
    @Column(name = "CD_TIP_FON_RCS")
    private short cdTipFonRcs;

    @Column(name = "NM_TIP_FON_RCS", nullable = false, length = 100)
    private String nmTipFonRcs;

    protected TipoFonteRecursos() {}

    public TipoFonteRecursos(short cdTipFonRcs, String nmTipFonRcs) {
        this.cdTipFonRcs = cdTipFonRcs;
        this.nmTipFonRcs = nmTipFonRcs;
    }

    public short getCdTipFonRcs()  { return cdTipFonRcs; }
    public String getNmTipFonRcs() { return nmTipFonRcs; }
}
