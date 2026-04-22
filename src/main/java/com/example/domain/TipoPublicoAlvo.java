package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Público Alvo (DB2GFG.TIP_PBCO_ALVO).
 */
@Entity
@Table(name = "TIP_PBCO_ALVO", schema = "DB2GFG")
public class TipoPublicoAlvo {

    @Id
    @Column(name = "CD_TIP_PBCO_ALVO")
    private short cdTipPbcoAlvo;

    @Column(name = "NM_TIP_PBCO_ALVO", nullable = false, length = 100)
    private String nmTipPbcoAlvo;

    protected TipoPublicoAlvo() {}

    public TipoPublicoAlvo(short cdTipPbcoAlvo, String nmTipPbcoAlvo) {
        this.cdTipPbcoAlvo = cdTipPbcoAlvo;
        this.nmTipPbcoAlvo = nmTipPbcoAlvo;
    }

    public short getCdTipPbcoAlvo()  { return cdTipPbcoAlvo; }
    public String getNmTipPbcoAlvo() { return nmTipPbcoAlvo; }
}
