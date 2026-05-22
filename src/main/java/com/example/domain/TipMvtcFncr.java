package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Movimentação Financeira (DB2GFG.TIP_MVTC_FNCR).
 *
 * Tabela de domínio identificada na query DET_MVT_FNCR do BI.
 * Exemplos de NM_TIP_MVTC_FNCR:
 *   'Abatimento no saldo honrado'
 *   'Devolução de valor recuperado'
 *   'Honra de garantia'
 *   'Recuperação do valor honrado'
 *   'A crédito do agente' (CD=2)
 *   'A movimentação'      (CD=3)
 */
@Entity
@Table(name = "TIP_MVTC_FNCR", schema = "DB2GFG")
public class TipMvtcFncr {

    @Id
    @Column(name = "CD_TIP_MVTC_FNCR")
    private short cdTipMvtcFncr;

    @Column(name = "NM_TIP_MVTC_FNCR", nullable = false, length = 100)
    private String nmTipMvtcFncr;

    protected TipMvtcFncr() {}

    public TipMvtcFncr(short cdTipMvtcFncr, String nmTipMvtcFncr) {
        this.cdTipMvtcFncr = cdTipMvtcFncr;
        this.nmTipMvtcFncr = nmTipMvtcFncr;
    }

    public short getCdTipMvtcFncr()  { return cdTipMvtcFncr; }
    public String getNmTipMvtcFncr() { return nmTipMvtcFncr; }
}
