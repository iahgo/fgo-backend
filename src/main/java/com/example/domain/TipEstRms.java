package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Estado de Remessa (DB2GFG.TIP_EST_RMS).
 *
 * Tabela de domínio identificada na query MOVT_FNCR do BI:
 *   INNER JOIN DB2GFG.TIP_EST_RMS G ON A.CD_TIP_EST_RMS = G.CD_TIP_EST_RMS
 *
 * Códigos observados nas telas:
 *   1 = Recebida
 *   2 = Em processamento
 *   3 = Concluída / Processada
 *   4 = Rejeitada
 *   5 = Cancelada
 */
@Entity
@Table(name = "TIP_EST_RMS", schema = "DB2GFG")
public class TipEstRms {

    @Id
    @Column(name = "CD_TIP_EST_RMS")
    private short cdTipEstRms;

    @Column(name = "NM_TIP_EST_RMS", nullable = false, length = 100)
    private String nmTipEstRms;

    protected TipEstRms() {}

    public TipEstRms(short cdTipEstRms, String nmTipEstRms) {
        this.cdTipEstRms = cdTipEstRms;
        this.nmTipEstRms = nmTipEstRms;
    }

    public short getCdTipEstRms()  { return cdTipEstRms; }
    public String getNmTipEstRms() { return nmTipEstRms; }
}
