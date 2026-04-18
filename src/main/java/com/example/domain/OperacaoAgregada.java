package com.example.domain;

import java.math.BigDecimal;

/**
 * Objeto de domínio que representa o resultado de uma query de agregação no DB2.
 *
 * Não é uma entidade JPA — não há @Entity porque não mapeamos a tabela inteira.
 * Representa exatamente o que o SQL de agregação retorna:
 *
 *   SELECT NM_PROGRAMA, COUNT(*), SUM(VLR_OPERACAO), COUNT(CASE WHEN inadimplente)
 *   FROM TB_OPERACAO
 *   WHERE CD_AGENTE = ? AND YEAR(...) = ? AND MONTH(...) = ?
 *   GROUP BY NM_PROGRAMA
 *
 * Este objeto pertence à camada de domínio e NUNCA é exposto diretamente na API.
 * O mapper converte OperacaoAgregada → OperacaoKpiDto para a resposta REST.
 *
 * Imutável por design: criado no repository, lido pelo service/loader.
 */
public final class OperacaoAgregada {

    private final String programa;
    private final long totalAtivas;
    private final BigDecimal vlrCarteira;
    private final long totalInad;

    public OperacaoAgregada(String programa, long totalAtivas, BigDecimal vlrCarteira, long totalInad) {
        this.programa    = programa;
        this.totalAtivas = totalAtivas;
        this.vlrCarteira = vlrCarteira;
        this.totalInad   = totalInad;
    }

    public String getPrograma()        { return programa; }
    public long getTotalAtivas()       { return totalAtivas; }
    public BigDecimal getVlrCarteira() { return vlrCarteira; }
    public long getTotalInad()         { return totalInad; }
}
