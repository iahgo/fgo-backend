package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Tipo de Motivo de Rejeição de Remessa (DB2GFG.TIP_MTV_RJC_RMS).
 *
 * Tabela de domínio identificada na query MOVT_FNCR do BI:
 *   LEFT JOIN DB2GFG.TIP_MTV_RJC_RMS F ON A.CD_MTV_RJC_RMS = F.CD_TIP_MTV_RJC_RMS
 *
 * Exemplos de valores (mapeados da tela Remessas):
 *   0 = 'Remessa válida'
 *   1 = 'Conteúdo'
 */
@Entity
@Table(name = "TIP_MTV_RJC_RMS", schema = "DB2GFG")
public class TipMtvRjcRms {

    @Id
    @Column(name = "CD_TIP_MTV_RJC_RMS")
    private short cdTipMtvRjcRms;

    @Column(name = "NM_TIP_MTV_RJC_RMS", nullable = false, length = 100)
    private String nmTipMtvRjcRms;

    protected TipMtvRjcRms() {}

    public TipMtvRjcRms(short cdTipMtvRjcRms, String nmTipMtvRjcRms) {
        this.cdTipMtvRjcRms = cdTipMtvRjcRms;
        this.nmTipMtvRjcRms = nmTipMtvRjcRms;
    }

    public short getCdTipMtvRjcRms()  { return cdTipMtvRjcRms; }
    public String getNmTipMtvRjcRms() { return nmTipMtvRjcRms; }
}
