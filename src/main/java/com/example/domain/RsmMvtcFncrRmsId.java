package com.example.domain;

import java.io.Serializable;
import java.util.Objects;

/** Chave composta para RsmMvtcFncrRms. */
public class RsmMvtcFncrRmsId implements Serializable {

    private int cdRmsAgtFnco;
    private short cdTipMvtcFncr;

    public RsmMvtcFncrRmsId() {}

    public RsmMvtcFncrRmsId(int cdRmsAgtFnco, short cdTipMvtcFncr) {
        this.cdRmsAgtFnco  = cdRmsAgtFnco;
        this.cdTipMvtcFncr = cdTipMvtcFncr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RsmMvtcFncrRmsId other)) return false;
        return cdRmsAgtFnco == other.cdRmsAgtFnco && cdTipMvtcFncr == other.cdTipMvtcFncr;
    }

    @Override
    public int hashCode() { return Objects.hash(cdRmsAgtFnco, cdTipMvtcFncr); }
}
