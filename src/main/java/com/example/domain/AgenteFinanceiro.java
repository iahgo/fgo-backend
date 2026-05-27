package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade mínima necessária para manter o Hibernate ORM ativo.
 * O Quarkus desativa EntityManager quando não há nenhuma @Entity.
 * Todos os repositórios usam createNativeQuery — esta entidade
 * existe apenas para que o CDI bean EntityManager seja produzido.
 */
@Entity
@Table(name = "AGT_FNCO", schema = "DB2GFG")
public class AgenteFinanceiro {

    @Id
    @Column(name = "CD_AGT_FNCO")
    private int codigoAgente;

    @Column(name = "NM_ABVD_AGT_FNCO")
    private String nomeAgente;

    protected AgenteFinanceiro() {}

    public int getCodigoAgente() { return codigoAgente; }
    public String getNomeAgente() { return nomeAgente; }
}
