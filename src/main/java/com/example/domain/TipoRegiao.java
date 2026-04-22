package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Região (DB2GFG.TIP_RGAO).
 */
@Entity
@Table(name = "TIP_RGAO", schema = "DB2GFG")
public class TipoRegiao {

    @Id
    @Column(name = "CD_TIP_RGAO")
    private short cdTipRgao;

    @Column(name = "NM_TIP_RGAO", nullable = false, length = 100)
    private String nmTipRgao;

    protected TipoRegiao() {}

    public TipoRegiao(short cdTipRgao, String nmTipRgao) {
        this.cdTipRgao = cdTipRgao;
        this.nmTipRgao = nmTipRgao;
    }

    public short getCdTipRgao()  { return cdTipRgao; }
    public String getNmTipRgao() { return nmTipRgao; }
}
