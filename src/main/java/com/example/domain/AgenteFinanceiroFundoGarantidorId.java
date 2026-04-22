package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Chave primária composta para AgenteFinanceiroFundoGarantidor (AGT_FNCO_FNDO_GRTR).
 * Necessário porque a tabela tem PK composta (CD_FNDO_GRTR, CD_AGT_FNCO).
 */
@Embeddable
public class AgenteFinanceiroFundoGarantidorId implements Serializable {

    @Column(name = "CD_FNDO_GRTR")
    private short cdFndoGrtr;

    @Column(name = "CD_AGT_FNCO")
    private int cdAgtFnco;

    protected AgenteFinanceiroFundoGarantidorId() {}

    public AgenteFinanceiroFundoGarantidorId(short cdFndoGrtr, int cdAgtFnco) {
        this.cdFndoGrtr = cdFndoGrtr;
        this.cdAgtFnco  = cdAgtFnco;
    }

    public short getCdFndoGrtr() { return cdFndoGrtr; }
    public int getCdAgtFnco()    { return cdAgtFnco; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgenteFinanceiroFundoGarantidorId other)) return false;
        return cdFndoGrtr == other.cdFndoGrtr && cdAgtFnco == other.cdAgtFnco;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cdFndoGrtr, cdAgtFnco);
    }
}
