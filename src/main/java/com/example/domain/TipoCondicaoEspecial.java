package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Condição Especial da Operação (DB2GFG.TIP_CND_ESPL_OPR).
 */
@Entity
@Table(name = "TIP_CND_ESPL_OPR", schema = "DB2GFG")
public class TipoCondicaoEspecial {

    @Id
    @Column(name = "CD_TIP_CND_ESPL")
    private short cdTipCndEspl;

    @Column(name = "NM_TIP_CND_ESPL", nullable = false, length = 100)
    private String nmTipCndEspl;

    protected TipoCondicaoEspecial() {}

    public TipoCondicaoEspecial(short cdTipCndEspl, String nmTipCndEspl) {
        this.cdTipCndEspl = cdTipCndEspl;
        this.nmTipCndEspl = nmTipCndEspl;
    }

    public short getCdTipCndEspl()  { return cdTipCndEspl; }
    public String getNmTipCndEspl() { return nmTipCndEspl; }
}
