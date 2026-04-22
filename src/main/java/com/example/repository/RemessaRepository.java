package com.example.repository;

import com.example.domain.RemessaAgente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a tabela DB2GFG.RMS_AGT_FNCO (remessas do agente).
 */
@ApplicationScoped
public class RemessaRepository {

    private static final Logger LOG = Logger.getLogger(RemessaRepository.class);

    @Inject
    EntityManager em;

    /**
     * Lista as remessas mais recentes de um agente, ordenadas da mais nova para a mais antiga.
     *
     * @param codAgente código interno do agente
     * @param limite    máximo de registros retornados
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<RemessaAgente> buscarPorAgente(int codAgente, int limite) {
        LOG.debugf("[REMESSA] buscarPorAgente agente=%d limite=%d", codAgente, limite);
        return em.createQuery("""
                SELECT r FROM RemessaAgente r
                WHERE r.cdAgtFnco = :codAgente
                ORDER BY r.tsRctRms DESC
                """, RemessaAgente.class)
                .setParameter("codAgente", codAgente)
                .setMaxResults(limite)
                .getResultList();
    }

    /**
     * Busca uma remessa específica pelo ID, validando que pertence ao agente.
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public Optional<RemessaAgente> buscarPorIdEAgente(int idRemessa, int codAgente) {
        LOG.debugf("[REMESSA] buscarPorIdEAgente id=%d agente=%d", idRemessa, codAgente);
        return em.createQuery("""
                SELECT r FROM RemessaAgente r
                WHERE r.cdRmsAgtFnco = :id
                  AND r.cdAgtFnco = :codAgente
                """, RemessaAgente.class)
                .setParameter("id", idRemessa)
                .setParameter("codAgente", codAgente)
                .getResultStream()
                .findFirst();
    }

    /**
     * Conta remessas por status para um agente — resumo rápido.
     * Retorna Object[] com [cdTipEstRms, quantidade].
     */
    @Transactional(Transactional.TxType.REQUIRED)
    public List<Object[]> contarPorStatus(int codAgente) {
        LOG.debugf("[REMESSA] contarPorStatus agente=%d", codAgente);
        return em.createQuery("""
                SELECT r.cdTipEstRms, COUNT(r)
                FROM RemessaAgente r
                WHERE r.cdAgtFnco = :codAgente
                GROUP BY r.cdTipEstRms
                ORDER BY r.cdTipEstRms
                """, Object[].class)
                .setParameter("codAgente", codAgente)
                .getResultList();
    }
}
