package com.example.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OperacaoResumoDto {

    private int codigoAgente;
    private List<OperacaoKpiDto> programas;
    private LocalDateTime carregadoEm;

    public OperacaoResumoDto() {}

    public OperacaoResumoDto(int codigoAgente, List<OperacaoKpiDto> programas, LocalDateTime carregadoEm) {
        this.codigoAgente = codigoAgente;
        this.programas    = programas;
        this.carregadoEm  = carregadoEm;
    }

    public int getCodigoAgente()                     { return codigoAgente; }
    public void setCodigoAgente(int v)               { this.codigoAgente = v; }
    public List<OperacaoKpiDto> getProgramas()       { return programas; }
    public void setProgramas(List<OperacaoKpiDto> v) { this.programas = v; }
    public LocalDateTime getCarregadoEm()            { return carregadoEm; }
    public void setCarregadoEm(LocalDateTime v)      { this.carregadoEm = v; }
}
