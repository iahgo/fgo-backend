package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA: Agente Financeiro (DB2GFG.AGT_FNCO).
 * Representa a instituição financeira que opera com o fundo garantidor.
 */
@Entity
@Table(name = "AGT_FNCO", schema = "DB2GFG")
public class AgenteFinanceiro {

    @Id
    @Column(name = "CD_AGT_FNCO")
    private int cdAgtFnco;

    @Column(name = "CD_CLI", nullable = false)
    private int cdCli;

    @Column(name = "NM_ABVD_AGT_FNCO", nullable = false, columnDefinition = "CHAR(60)")
    private String nmAbvdAgtFnco;

    protected AgenteFinanceiro() {}

    public AgenteFinanceiro(int cdAgtFnco, int cdCli, String nmAbvdAgtFnco) {
        this.cdAgtFnco      = cdAgtFnco;
        this.cdCli          = cdCli;
        this.nmAbvdAgtFnco  = nmAbvdAgtFnco;
    }

    public int getCdAgtFnco()         { return cdAgtFnco; }
    public int getCdCli()             { return cdCli; }
    public String getNmAbvdAgtFnco()  { return nmAbvdAgtFnco; }
}
