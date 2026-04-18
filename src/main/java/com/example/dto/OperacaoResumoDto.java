package com.example.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de resposta do endpoint GET /api/operacoes/{mesAno}.
 *
 * Este objeto é:
 *   1. Serializado para JSON e armazenado no Redis (loader)
 *   2. Desserializado do Redis e retornado na API (service)
 *
 * Chave Redis onde este objeto é armazenado:
 *   af:{codAgente}:operacao:resumo:{mesAno}
 *
 * Exemplo de resposta JSON:
 * {
 *   "codAgente": 8,
 *   "mesAno": "2025-04",
 *   "programas": [
 *     { "programa": "PRONAMPE", "totalAtivas": 380000, "vlrCarteira": 1500000000, "totalInad": 2100, "taxaInad": 0.005526 }
 *   ],
 *   "carregadoEm": "2025-04-17T07:20:15"
 * }
 */
public class OperacaoResumoDto {

    private int codAgente;
    private String mesAno;
    private List<OperacaoKpiDto> programas;

    /** Momento em que o loader gravou estes dados no Redis. Útil para auditoria. */
    private LocalDateTime carregadoEm;

    public OperacaoResumoDto() {}

    public OperacaoResumoDto(int codAgente, String mesAno, List<OperacaoKpiDto> programas,
                             LocalDateTime carregadoEm) {
        this.codAgente   = codAgente;
        this.mesAno      = mesAno;
        this.programas   = programas;
        this.carregadoEm = carregadoEm;
    }

    public int getCodAgente()               { return codAgente; }
    public void setCodAgente(int v)         { this.codAgente = v; }

    public String getMesAno()               { return mesAno; }
    public void setMesAno(String v)         { this.mesAno = v; }

    public List<OperacaoKpiDto> getProgramas()           { return programas; }
    public void setProgramas(List<OperacaoKpiDto> v)     { this.programas = v; }

    public LocalDateTime getCarregadoEm()                { return carregadoEm; }
    public void setCarregadoEm(LocalDateTime v)          { this.carregadoEm = v; }
}
