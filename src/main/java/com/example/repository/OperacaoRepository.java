package com.example.repository;

import com.example.domain.OperacaoAgregada;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de acesso ao DB2 para o domínio de Operações.
 *
 * Responsabilidade única: executar queries SQL no DB2 e retornar objetos de domínio.
 * Não conhece Redis, DTOs, regras de negócio ou TTL.
 *
 * Usa JDBC direto (sem JPA/Hibernate) porque:
 *   - As queries são de AGREGAÇÃO — não mapeamos entidades linha a linha
 *   - JPA geraria overhead de mapeamento desnecessário para resultsets agregados
 *   - Controle total sobre o SQL executado (performance crítica com tabela de 400 GB)
 *
 * =========================================================================
 * IMPORTANTE: AJUSTE OS NOMES DE TABELA E COLUNAS PARA O SEU SCHEMA DB2
 * =========================================================================
 * Antes de usar em produção, adapte:
 *   - TB_OPERACAO         → nome real da sua tabela
 *   - CD_AGENTE           → código do agente
 *   - NM_PROGRAMA         → nome do programa de crédito
 *   - VLR_OPERACAO        → valor da operação
 *   - STATUS              → status da operação
 *   - 'INADIMPLENTE'      → valor de status para inadimplência
 *   - DT_REFERENCIA       → data de referência
 */
@ApplicationScoped
public class OperacaoRepository {

    private static final Logger LOG = Logger.getLogger(OperacaoRepository.class);

    @Inject
    AgroalDataSource dataSource;

    // =========================================================================
    // QUERY PRINCIPAL: KPIs AGREGADOS POR AGENTE E MÊS
    // =========================================================================

    /**
     * Retorna os KPIs agregados de operações para um agente em um mês específico.
     *
     * A query usa YEAR() e MONTH() do DB2 para filtrar pelo mês de referência.
     * Alternativa: VARCHAR_FORMAT(DT_REFERENCIA, 'YYYY-MM') = ?
     *
     * @param codAgente código interno do agente (ex: 8 = Itaú)
     * @param ano       ano de referência (ex: 2025)
     * @param mes       mês de referência (ex: 4 para abril)
     * @return lista de agregados por programa (PRONAMPE, FGI, etc.)
     * @throws OperacaoRepositoryException em falha de comunicação com o DB2
     */
    public List<OperacaoAgregada> buscarAgregadoPorAgenteMes(int codAgente, int ano, int mes) {
        // =====================================================================
        // AJUSTE ESTE SQL PARA O SEU SCHEMA REAL
        // =====================================================================
        final String sql = """
                SELECT
                    NM_PROGRAMA,
                    COUNT(*)                                             AS TOTAL_ATIVAS,
                    COALESCE(SUM(VLR_OPERACAO), 0)                      AS VLR_CARTEIRA,
                    COUNT(CASE WHEN STATUS = 'INADIMPLENTE' THEN 1 END) AS TOTAL_INAD
                FROM TB_OPERACAO
                WHERE CD_AGENTE         = ?
                  AND YEAR(DT_REFERENCIA)  = ?
                  AND MONTH(DT_REFERENCIA) = ?
                GROUP BY NM_PROGRAMA
                ORDER BY VLR_CARTEIRA DESC
                """;
        // =====================================================================

        LOG.debugf("[REPOSITORY-DB2] agente=%d ano=%d mes=%d — executando query de agregação", codAgente, ano, mes);

        List<OperacaoAgregada> resultado = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, codAgente);
            ps.setInt(2, ano);
            ps.setInt(3, mes);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new OperacaoAgregada(
                            rs.getString("NM_PROGRAMA"),
                            rs.getLong("TOTAL_ATIVAS"),
                            rs.getBigDecimal("VLR_CARTEIRA"),
                            rs.getLong("TOTAL_INAD")
                    ));
                }
            }

        } catch (Exception e) {
            throw new OperacaoRepositoryException(
                    String.format("Falha ao consultar DB2 — agente=%d ano=%d mes=%d", codAgente, ano, mes), e);
        }

        LOG.debugf("[REPOSITORY-DB2] agente=%d — %d programa(s) retornados", codAgente, resultado.size());
        return resultado;
    }

    // =========================================================================
    // QUERY AUXILIAR: LISTA DE AGENTES ATIVOS NO DB2
    // =========================================================================

    /**
     * Retorna os códigos de agentes que têm operações ativas no DB2.
     *
     * Alternativa ao uso de fgo.agentes.codigos no application.properties.
     * Use este método quando quiser que a lista de agentes seja dinâmica
     * (baseada no que existe no banco, não em configuração estática).
     *
     * =====================================================================
     * AJUSTE ESTA QUERY PARA O SEU SCHEMA REAL
     * =====================================================================
     *
     * @throws OperacaoRepositoryException em falha de comunicação com o DB2
     */
    public List<Integer> buscarAgentesAtivos() {
        final String sql = """
                SELECT DISTINCT CD_AGENTE
                FROM TB_OPERACAO
                WHERE DT_REFERENCIA >= CURRENT_DATE - 30 DAYS
                ORDER BY CD_AGENTE
                """;

        List<Integer> agentes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                agentes.add(rs.getInt("CD_AGENTE"));
            }

        } catch (Exception e) {
            throw new OperacaoRepositoryException("Falha ao buscar agentes ativos no DB2", e);
        }

        LOG.debugf("[REPOSITORY-DB2] %d agentes ativos encontrados no DB2", agentes.size());
        return agentes;
    }

    // =========================================================================
    // EXCEPTION INTERNA DO REPOSITÓRIO
    // =========================================================================

    /**
     * Exceção específica do repositório DB2.
     * Envolve SQLException em uma exceção de runtime sem amarrar as camadas
     * superiores com checked exceptions de JDBC.
     */
    public static class OperacaoRepositoryException extends RuntimeException {
        public OperacaoRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
