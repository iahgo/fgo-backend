package com.example.repository;

import com.example.domain.AgenteAgregado;
import com.example.domain.AgenteFinanceiro;
import com.example.domain.OperacaoAgregada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Repositório de acesso ao DB2 para o domínio de Operações.
 *
 * Usa JPA (EntityManager + JPQL) — os campos referenciados nas queries são
 * os nomes das variáveis Java das entidades, não os nomes de coluna do DB2.
 * O mapeamento Java → coluna DB2 está concentrado nos @Column de cada entidade.
 *
 * Schema: DB2GFG
 * Entidade principal: OperacaoCreditoFundoGarantidor
 * Agentes: AgenteFinanceiro
 */
@ApplicationScoped
public class OperacaoRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRepository.class);

    @Inject
    EntityManager em;

    // =========================================================================
    // QUERY PRINCIPAL: KPIs AGREGADOS POR AGENTE E MÊS
    // =========================================================================

    /**
     * Retorna os KPIs agregados por programa de crédito para um agente em um mês.
     *
     * A query usa JPQL com cross-join entre OperacaoCreditoFundoGarantidor e
     * TipoProgramaCredito, referenciando apenas nomes de variáveis Java.
     * EXTRACT é padrão JPQL 2.2 — suportado pelo Hibernate 6 (Quarkus 3).
     *
     * Agregações:
     *   - programa      → p.nmTipPgmCrd
     *   - totalAtivas   → COUNT(o)
     *   - vlrCarteira   → SUM(o.vlSdoCptlNmld)   (saldo de capital nominal)
     *   - totalInad     → SUM(CASE WHEN saldo em atraso > 0)
     *
     * @param codAgente código do agente (campo cdAgtFnco na entidade)
     * @param ano       ano de referência
     * @param mes       mês de referência (1–12)
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<OperacaoAgregada> buscarAgregadoPorAgenteMes(int codAgente, int ano, int mes) {
        LOG.debugf("[REPOSITORY] agente=%d ano=%d mes=%d — executando agregação JPQL", codAgente, ano, mes);

        TypedQuery<OperacaoAgregada> query = em.createQuery("""
                SELECT NEW com.example.domain.OperacaoAgregada(
                    p.nmTipPgmCrd,
                    COUNT(o),
                    SUM(o.vlSdoCptlNmld),
                    SUM(CASE WHEN o.vlSdoCptlAtr > 0 THEN 1 ELSE 0 END),
                    SUM(o.vlSdoCptlAtr),
                    SUM(o.vlGrtOprAjsd)
                )
                FROM OperacaoCreditoFundoGarantidor o, TipoProgramaCredito p
                WHERE p.cdTipPgmCrd = o.cdTipPgmCrd
                  AND o.cdAgtFnco = :codAgente
                  AND EXTRACT(YEAR FROM o.dtFrmzOpr) = :ano
                  AND EXTRACT(MONTH FROM o.dtFrmzOpr) = :mes
                GROUP BY p.nmTipPgmCrd
                ORDER BY SUM(o.vlSdoCptlNmld) DESC
                """, OperacaoAgregada.class)
                .setParameter("codAgente", codAgente)
                .setParameter("ano", ano)
                .setParameter("mes", mes);

        List<OperacaoAgregada> resultado = query.getResultList();
        LOG.debugf("[REPOSITORY] agente=%d — %d programa(s) retornados", codAgente, resultado.size());
        return resultado;
    }

    // =========================================================================
    // QUERY AUXILIAR: LISTA DE AGENTES ATIVOS
    // =========================================================================

    /**
     * Retorna todos os códigos de agentes cadastrados em AgenteFinanceiro.
     * Todo agente presente na tabela mestre é considerado ativo por definição.
     *
     * A query referencia o campo Java {@code cdAgtFnco} — mapeado para a
     * coluna DB2 CD_AGT_FNCO via @Column na entidade AgenteFinanceiro.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<Integer> buscarAgentesAtivos() {
        List<Integer> agentes = em.createQuery("""
                SELECT a.cdAgtFnco
                FROM AgenteFinanceiro a
                ORDER BY a.cdAgtFnco
                """, Integer.class)
                .getResultList();

        LOG.debugf("[REPOSITORY] %d agentes encontrados", agentes.size());
        return agentes;
    }

    // =========================================================================
    // QUERY: TODOS OS AGENTES COM NOME
    // =========================================================================

    /**
     * Retorna todos os agentes com código e nome — usado no endpoint GET /api/agentes.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<AgenteFinanceiro> buscarTodosAgentes() {
        return em.createQuery("""
                SELECT a FROM AgenteFinanceiro a ORDER BY a.cdAgtFnco
                """, AgenteFinanceiro.class)
                .getResultList();
    }

    // =========================================================================
    // QUERIES CONSOLIDADAS (todos os agentes) — endpoint admin
    // =========================================================================

    /**
     * Agrega operações de TODOS os agentes por programa de crédito para um mês.
     * Usado no endpoint de visão consolidada (sem filtro por agente).
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<OperacaoAgregada> buscarConsolidadoPorPrograma(int ano, int mes) {
        LOG.debugf("[REPOSITORY] consolidado por programa — ano=%d mes=%d", ano, mes);
        return em.createQuery("""
                SELECT NEW com.example.domain.OperacaoAgregada(
                    p.nmTipPgmCrd,
                    COUNT(o),
                    SUM(o.vlSdoCptlNmld),
                    SUM(CASE WHEN o.vlSdoCptlAtr > 0 THEN 1 ELSE 0 END),
                    SUM(o.vlSdoCptlAtr),
                    SUM(o.vlGrtOprAjsd)
                )
                FROM OperacaoCreditoFundoGarantidor o, TipoProgramaCredito p
                WHERE p.cdTipPgmCrd = o.cdTipPgmCrd
                  AND EXTRACT(YEAR FROM o.dtFrmzOpr) = :ano
                  AND EXTRACT(MONTH FROM o.dtFrmzOpr) = :mes
                GROUP BY p.nmTipPgmCrd
                ORDER BY SUM(o.vlSdoCptlNmld) DESC
                """, OperacaoAgregada.class)
                .setParameter("ano", ano)
                .setParameter("mes", mes)
                .getResultList();
    }

    /**
     * Agrega operações de TODOS os agentes, retornando uma linha por agente.
     * Usado no endpoint de visão consolidada para ranking de carteira.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<AgenteAgregado> buscarConsolidadoPorAgente(int ano, int mes) {
        LOG.debugf("[REPOSITORY] consolidado por agente — ano=%d mes=%d", ano, mes);
        return em.createQuery("""
                SELECT NEW com.example.domain.AgenteAgregado(
                    a.cdAgtFnco,
                    a.nmAbvdAgtFnco,
                    COUNT(o),
                    SUM(o.vlSdoCptlNmld),
                    SUM(CASE WHEN o.vlSdoCptlAtr > 0 THEN 1 ELSE 0 END),
                    SUM(o.vlSdoCptlAtr),
                    SUM(o.vlGrtOprAjsd)
                )
                FROM OperacaoCreditoFundoGarantidor o, AgenteFinanceiro a
                WHERE a.cdAgtFnco = o.cdAgtFnco
                  AND EXTRACT(YEAR FROM o.dtFrmzOpr) = :ano
                  AND EXTRACT(MONTH FROM o.dtFrmzOpr) = :mes
                GROUP BY a.cdAgtFnco, a.nmAbvdAgtFnco
                ORDER BY SUM(o.vlSdoCptlNmld) DESC
                """, AgenteAgregado.class)
                .setParameter("ano", ano)
                .setParameter("mes", mes)
                .getResultList();
    }
}
