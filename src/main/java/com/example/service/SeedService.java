package com.example.service;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço de geração de massa de dados para testes de carga.
 * Usa JDBC batch inserts diretamente para máxima performance.
 * Commita a cada BATCH_SIZE linhas para evitar log overflow no DB2.
 */
@ApplicationScoped
public class SeedService {

    private static final Logger LOG = Logger.getLogger(SeedService.class);
    private static final int BATCH_SIZE = 10_000; // linhas por executeBatch() + commit

    @Inject
    AgroalDataSource dataSource;

    // Estado compartilhado (leitura pelo endpoint /status)
    private final AtomicLong progresso   = new AtomicLong(0);
    private volatile long     total      = 0;
    private volatile boolean  emExecucao = false;
    private volatile String   statusMsg  = "Ocioso";

    // =====================================================================
    // Arrays para geração aleatória
    // =====================================================================
    private static final int[]    AGENTES    = {1, 2, 3, 4, 5, 6, 7};
    private static final int[]    FUNDOS     = {1, 2};
    private static final short[]  PROGRAMAS  = {1, 2, 3, 4, 5, 6};
    private static final short[]  MODALIDS   = {1, 2, 3, 4, 5, 6};
    private static final short[]  FINALIDS   = {1, 2, 3, 4, 5, 6};
    private static final short[]  TIPOS_PSS  = {1, 2, 3};
    private static final short[]  PUBLICOS   = {1, 2, 3, 4, 5};
    private static final short[]  CONDICOES  = {1, 2, 3, 4};
    private static final short[]  FONTES     = {1, 2, 3, 4};
    private static final short[]  CRNG_AMTZ  = {1, 2, 3};
    private static final short[]  TIPOS_FRMZ = {1, 2, 3};
    private static final int[]    IBGE_MUNS  = {
        5300108, 3550308, 1302603, 4106902, 2304400,
        3304557, 3106200, 5002704, 1501402, 2611606,
        2927408, 3205309, 4314902, 1100205, 2111300,
        4205407, 2704302, 3170206, 5103403, 3509502
    };
    private static final String[] NIVEIS_RISCO = {"A1", "A2", "B1", "B2", "C1", "C2", "D ", "E "};

    // =====================================================================
    // API pública
    // =====================================================================

    public boolean isEmExecucao() { return emExecucao; }
    public long    getProgresso()  { return progresso.get(); }
    public long    getTotal()      { return total; }
    public String  getStatusMsg()  { return statusMsg; }

    public void gerarDados(long quantidade, boolean limpar) {
        if (emExecucao) {
            LOG.warn("[SEED] Geração já em andamento. Ignorando.");
            return;
        }
        emExecucao = true;
        progresso.set(0);
        total = quantidade;
        statusMsg = "Iniciando...";
        long inicio = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            if (limpar) {
                statusMsg = "Limpando dados existentes...";
                limparDados(conn);
                statusMsg = "Reinserindo dados mestres...";
                reinserirMasterData(conn);
            }

            statusMsg = "Gerando operações...";
            gerarOperacoes(conn, quantidade);

            statusMsg = "Gerando remessas...";
            gerarRemessas(conn);

            long segundos = (System.currentTimeMillis() - inicio) / 1000;
            statusMsg = String.format("Concluído: %d operações + remessas em %ds", quantidade, segundos);
            LOG.infof("[SEED] %s", statusMsg);

        } catch (Exception e) {
            statusMsg = "ERRO: " + e.getMessage();
            LOG.errorf("[SEED] Falha: %s", e.getMessage(), e);
        } finally {
            emExecucao = false;
        }
    }

    // =====================================================================
    // Limpeza (respeitando ordem das FKs)
    // =====================================================================

    private void limparDados(Connection conn) throws SQLException {
        LOG.info("[SEED] Limpando tabelas...");

        // OPR_CRD_FNDO_GRTR: pode ter milhões de linhas → TRUNCATE (sem log por linha).
        // DB2 só permite TRUNCATE quando a tabela não é referenciada por FK de outra tabela com dados.
        // Como OPR_CRD_FNDO_GRTR é folha (ninguém aponta para ela), TRUNCATE funciona.
        conn.commit();         // fecha transação aberta antes de trocar autoCommit
        conn.setAutoCommit(true);
        try (Statement st = conn.createStatement()) {
            st.execute("TRUNCATE TABLE DB2GFG.OPR_CRD_FNDO_GRTR IMMEDIATE");
            LOG.info("[SEED] OPR_CRD_FNDO_GRTR truncada");
            st.execute("TRUNCATE TABLE DB2GFG.RMS_AGT_FNCO IMMEDIATE");
            LOG.info("[SEED] RMS_AGT_FNCO truncada");
        } finally {
            conn.setAutoCommit(false);
        }

        // As demais tabelas têm pouquíssimas linhas → DELETE simples, sem risco de log overflow.
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM DB2GFG.AGT_FNCO_FNDO_GRTR"); conn.commit();
            st.execute("DELETE FROM DB2GFG.AGT_FNCO");            conn.commit();
            st.execute("DELETE FROM DB2GFG.FNDO_GRTR");           conn.commit();
            st.execute("DELETE FROM DB2GFG.CT_MVT_GRT_OPR");      conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_PGM_CRD");         conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_FON_RCS");         conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_CND_ESPL_OPR");    conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_PBCO_ALVO");       conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_EST_OPR");         conn.commit();
            st.execute("DELETE FROM DB2GFG.TIP_RGAO");            conn.commit();
        }
        LOG.info("[SEED] Dados apagados.");
    }

    // =====================================================================
    // Reinserção das tabelas de domínio e mestres
    // =====================================================================

    private void reinserirMasterData(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {

            st.execute("INSERT INTO DB2GFG.TIP_EST_OPR VALUES (1, 'Ativa')");
            st.execute("INSERT INTO DB2GFG.TIP_EST_OPR VALUES (2, 'Encerrada')");
            st.execute("INSERT INTO DB2GFG.TIP_EST_OPR VALUES (3, 'Inadimplente')");
            st.execute("INSERT INTO DB2GFG.TIP_EST_OPR VALUES (4, 'Liquidada')");
            st.execute("INSERT INTO DB2GFG.TIP_EST_OPR VALUES (5, 'Honrada')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.TIP_PBCO_ALVO VALUES (1, 'Microempresa')");
            st.execute("INSERT INTO DB2GFG.TIP_PBCO_ALVO VALUES (2, 'Empresa de Pequeno Porte')");
            st.execute("INSERT INTO DB2GFG.TIP_PBCO_ALVO VALUES (3, 'Pessoa Fisica')");
            st.execute("INSERT INTO DB2GFG.TIP_PBCO_ALVO VALUES (4, 'Produtor Rural')");
            st.execute("INSERT INTO DB2GFG.TIP_PBCO_ALVO VALUES (5, 'Media Empresa')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.TIP_CND_ESPL_OPR VALUES (1, 'Sem condicao especial')");
            st.execute("INSERT INTO DB2GFG.TIP_CND_ESPL_OPR VALUES (2, 'COVID-19')");
            st.execute("INSERT INTO DB2GFG.TIP_CND_ESPL_OPR VALUES (3, 'Calamidade publica')");
            st.execute("INSERT INTO DB2GFG.TIP_CND_ESPL_OPR VALUES (4, 'Programa emergencial')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.TIP_FON_RCS VALUES (1, 'BNDES')");
            st.execute("INSERT INTO DB2GFG.TIP_FON_RCS VALUES (2, 'Tesouro Nacional')");
            st.execute("INSERT INTO DB2GFG.TIP_FON_RCS VALUES (3, 'FAT')");
            st.execute("INSERT INTO DB2GFG.TIP_FON_RCS VALUES (4, 'Recursos Proprios')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (1, 'PRONAMPE')");
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (2, 'FGI')");
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (3, 'PEAC')");
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (4, 'Emergencial Investimento')");
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (5, 'FGO Rural')");
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (6, 'FGO Geral')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.TIP_RGAO VALUES (1, 'Norte')");
            st.execute("INSERT INTO DB2GFG.TIP_RGAO VALUES (2, 'Nordeste')");
            st.execute("INSERT INTO DB2GFG.TIP_RGAO VALUES (3, 'Centro-Oeste')");
            st.execute("INSERT INTO DB2GFG.TIP_RGAO VALUES (4, 'Sudeste')");
            st.execute("INSERT INTO DB2GFG.TIP_RGAO VALUES (5, 'Sul')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.CT_MVT_GRT_OPR (NR_SEQL_CT_MVT, NM_CT_MVT_GRT_OPR) VALUES (1, 'Conta principal FGO')");
            st.execute("INSERT INTO DB2GFG.CT_MVT_GRT_OPR (NR_SEQL_CT_MVT, NM_CT_MVT_GRT_OPR) VALUES (2, 'Conta principal FGI')");
            st.execute("INSERT INTO DB2GFG.CT_MVT_GRT_OPR (NR_SEQL_CT_MVT, NM_CT_MVT_GRT_OPR) VALUES (3, 'Conta reserva')");
            conn.commit();

            st.execute("INSERT INTO DB2GFG.FNDO_GRTR (CD_FNDO_GRTR, SG_FNDO_GRTR, NM_FNDO_GRTR, NR_AG_CT_MVT_FNDO, NR_CT_MVT_FNDO, CD_CIA_CTB, IDT_ULT_FCHT_BAL, DT_INC_FNDO_GRTR, DT_ECR_FNDO_GRTR, CD_TIP_EST_ENDO, CD_USU_RSP_ATL_REG, CD_USU_RSP_VLDC, TS_ATL_REG) VALUES (1, 'FGO', 'Fundo de Garantia de Operacoes', 1001, 123456789, 'BB1', '2026-03-31', '2010-01-01', '2035-12-31', 1, 'USRSIST', 'USRVLDC', '2026-04-01 08:00:00')");
            st.execute("INSERT INTO DB2GFG.FNDO_GRTR (CD_FNDO_GRTR, SG_FNDO_GRTR, NM_FNDO_GRTR, NR_AG_CT_MVT_FNDO, NR_CT_MVT_FNDO, CD_CIA_CTB, IDT_ULT_FCHT_BAL, DT_INC_FNDO_GRTR, DT_ECR_FNDO_GRTR, CD_TIP_EST_ENDO, CD_USU_RSP_ATL_REG, CD_USU_RSP_VLDC, TS_ATL_REG) VALUES (2, 'FGI', 'Fundo de Garantia para Investimentos', 1001, 987654321, 'BB1', '2026-03-31', '2012-06-01', '2035-12-31', 1, 'USRSIST', 'USRVLDC', '2026-04-01 08:00:00')");
            conn.commit();

            // Agentes
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (1, 1001, 'BANCO DO BRASIL SA')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (2, 1002, 'CAIXA ECONOMICA FEDERAL')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (3, 1003, 'BRADESCO SA')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (4, 1004, 'ITAU UNIBANCO SA')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (5, 1005, 'SANTANDER BR SA')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (6, 1006, 'SICREDI')");
            st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES (7, 1007, 'SICOOB')");
            conn.commit();

            // Associações agente × fundo (todos os agentes para ambos os fundos)
            String[][] ispbs = {
                {"1","00000000","1001"}, {"2","36170101","1002"}, {"3","60746948","1003"},
                {"4","60872504","1004"}, {"5","90400888","1005"}, {"6","01181521","1006"},
                {"7","02038232","1007"}
            };
            for (int fundo : new int[]{1, 2}) {
                for (String[] a : ispbs) {
                    int agId = Integer.parseInt(a[0]);
                    st.execute("INSERT INTO DB2GFG.AGT_FNCO_FNDO_GRTR (CD_FNDO_GRTR, CD_AGT_FNCO, CD_TIP_EST_AGT, CD_TIP_ITCB_FNCO, CD_ISPB, NR_CTR_ITCB_DADO, CD_CLI, CD_PRD, NR_CT_DEP_AGT, NR_AG_CT_DEP_AGT, CD_USU_RSP_VLDC) VALUES (" + fundo + ", " + agId + ", 1, 1, '" + a[1] + "', " + (fundo * 100 + agId) + ", " + a[2] + ", 1, " + (100000000 + agId * 11111111 + fundo * 1000000) + ", " + a[2] + ", 'USRVLDC')");
                }
            }
            conn.commit();
        }
        LOG.info("[SEED] Dados mestres reinseridos.");
    }

    // =====================================================================
    // Geração das operações (JDBC batch)
    // =====================================================================

    private void gerarOperacoes(Connection conn, long quantidade) throws SQLException {
        LOG.infof("[SEED] Iniciando geração de %d operações (lote=%d)...", quantidade, BATCH_SIZE);

        String sql = """
            INSERT INTO DB2GFG.OPR_CRD_FNDO_GRTR (
              CD_OPR_CRD_FNDO, CD_FNDO_GRTR, CD_AGT_FNCO, CD_IDFR_EXNO_OPR,
              CD_TIP_MDLD_CRD, CD_TIP_FNLD_CRD, CD_TIP_PSS, NR_AG_CTRT_OPR,
              CD_IBGE_MUN_AG, CD_IDFR_SRF, CD_RADC_CNPJ, CD_TIP_PBCO_ALVO,
              VL_FATM_BRTO_AAL, VL_OPR_CRD, PC_GRT_OPR_CRD, CD_TIP_CND_ESPL,
              DT_FRMZ_OPR, DT_VNCT_OPR, VL_SDO_CPTL_NMLD, VL_SDO_CPTL_ATR,
              VL_SDO_ENCG_NMLD, VL_SDO_ENCG_ATR, CD_NVL_RSCO_OPR, DT_SDO_OPR,
              VL_GRT_OPR_AJSD, VL_TTL_LIBD_OPR, DT_PRMO_SDO_ATR, CD_TIP_CRNG_AMTZ,
              CD_TIP_EST_OPR, DT_DPC_EXNO_OPR, IC_FTR_CMSS, CD_TIP_FON_RCS,
              CD_TIP_PGM_CRD, CD_TIP_FRMZ, CD_ENDO_GRTR, IN_FATM_VLDD_SRF,
              IN_OPR_CADD_BC, VL_SBS_CRD, CD_SEXO_QLF_OPR, NR_CPF_QLF_OPR,
              IC_PDA_CRD, CD_TIP_RGAO, CD_BC_OPR_CRD, NR_SEQL_CT_MVT,
              NR_CPF_MTR, CD_CNPJ_MTR, CD_RADC_CNPJ_MTR
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            long noBatch = 0;
            for (long i = 1; i <= quantidade; i++) {
                preencherLinha(ps, i, rnd);
                ps.addBatch();
                noBatch++;

                if (noBatch >= BATCH_SIZE) {
                    executarBatchComLog(ps, conn, i);
                    progresso.set(i);
                    noBatch = 0;
                    statusMsg = String.format("Inserindo: %d/%d (%.1f%%)", i, quantidade, 100.0 * i / quantidade);
                    LOG.infof("[SEED] %s", statusMsg);
                }
            }
            if (noBatch > 0) {
                executarBatchComLog(ps, conn, quantidade);
                progresso.set(quantidade);
            }
        }
    }

    /**
     * Executa o batch e, em caso de falha, extrai todas as causas raiz via getNextException().
     * O DB2 encapsula erros individuais — sem este método o log mostra apenas ERRORCODE=-4229.
     */
    private void executarBatchComLog(PreparedStatement ps, Connection conn, long atéLinha) throws SQLException {
        try {
            ps.executeBatch();
            conn.commit();
        } catch (java.sql.BatchUpdateException bue) {
            LOG.errorf("[SEED] Batch falhou na linha ~%d. Causas raiz:", atéLinha);
            Throwable causa = bue.getNextException();
            int idx = 0;
            while (causa != null) {
                LOG.errorf("  [%d] %s", idx++, causa.getMessage());
                causa = (causa instanceof SQLException) ? ((SQLException) causa).getNextException() : null;
            }
            conn.rollback();
            throw bue;
        }
    }

    // =====================================================================
    // Geração de remessas (RMS_AGT_FNCO)
    // =====================================================================

    /**
     * Gera 1 remessa por agente por fundo por dia de 2024-01-01 até hoje.
     * Total: 7 agentes × 2 fundos × ~843 dias ≈ 11.800 registros.
     *
     * Distribuição de status (dias passados — exceto hoje):
     *   70% Processada (3), 15% Recebida (1), 10% Rejeitada (4), 5% Cancelada (5)
     * Dia atual: sempre Recebida (chegou hoje, ainda não processou).
     */
    private void gerarRemessas(Connection conn) throws SQLException {
        LOG.info("[SEED] Gerando remessas diárias (RMS_AGT_FNCO)...");

        String sql = """
            INSERT INTO DB2GFG.RMS_AGT_FNCO (
              CD_RMS_AGT_FNCO, CD_FNDO_GRTR, CD_AGT_FNCO, NR_SEQL_RMS, NM_DTST,
              DT_VRS_LAUT, TS_RCT_RMS, DT_PRCT_EVT, DT_CTC_UTZD_PRCT,
              DT_ATL_MNTR, DT_MVTC_FNCR, CD_MTV_RJC_RMS, VL_LODO_MVTC_RMS,
              CD_TIP_NTZ_MVTC, CD_TIP_EST_RMS, QT_REG_RMS
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDate inicio  = LocalDate.of(2024, 1, 1);
        LocalDate hoje    = LocalDate.now();
        int id = 1;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int agente : AGENTES) {
                for (int fundo : FUNDOS) {
                    short seql = 1;
                    for (LocalDate dia = inicio; !dia.isAfter(hoje); dia = dia.plusDays(1), seql++) {

                        // Nome do arquivo: AGT{agente}_F{fundo}_{YYYYMMDD}.txt (pad para 44 chars)
                        String nmDtst = String.format("%-44s",
                            String.format("AGT%03d_F%d_%s.txt",
                                agente, fundo,
                                dia.toString().replace("-", "")))
                            .substring(0, 44);

                        // Chegada: 07h–09h do dia de referência
                        LocalDateTime tsRct = dia.atTime(7 + rnd.nextInt(3), rnd.nextInt(60));

                        // Status: dia atual = Recebida; dias anteriores com distribuição
                        short estRms;
                        if (dia.equals(hoje)) {
                            estRms = 1; // Recebida (chegou hoje)
                        } else {
                            int roll = rnd.nextInt(20);
                            estRms = roll < 14 ? (short)3   // 70% Processada
                                   : roll < 17 ? (short)1   // 15% Recebida
                                   : roll < 19 ? (short)4   // 10% Rejeitada
                                               : (short)5;  //  5% Cancelada
                        }

                        LocalDate dtPrct   = (estRms == 3) ? dia.plusDays(1) : null;
                        LocalDate dtAtl    = dtPrct;
                        LocalDate dtMvtc   = (dtPrct != null) ? dtPrct.plusDays(rnd.nextInt(2)) : null;
                        short cdMtvRjc     = (estRms == 4) ? (short)(1 + rnd.nextInt(5)) : (short)0;
                        int qtdReg         = 10_000 + rnd.nextInt(40_000);
                        double vlLodo      = Math.round(qtdReg * (50_000 + rnd.nextDouble() * 450_000) * 100.0) / 100.0;

                        int col = 1;
                        ps.setInt       (col++, id++);
                        ps.setShort     (col++, (short) fundo);
                        ps.setInt       (col++, agente);
                        ps.setShort     (col++, seql);
                        ps.setString    (col++, nmDtst);
                        ps.setDate      (col++, Date.valueOf(dia));
                        ps.setTimestamp (col++, Timestamp.valueOf(tsRct));
                        if (dtPrct != null) ps.setDate(col++, Date.valueOf(dtPrct));
                        else ps.setNull (col++, Types.DATE);
                        if (dtPrct != null) ps.setDate(col++, Date.valueOf(dtPrct));
                        else ps.setNull (col++, Types.DATE);
                        if (dtAtl != null)  ps.setDate(col++, Date.valueOf(dtAtl));
                        else ps.setNull (col++, Types.DATE);
                        if (dtMvtc != null) ps.setDate(col++, Date.valueOf(dtMvtc));
                        else ps.setNull (col++, Types.DATE);
                        ps.setShort     (col++, cdMtvRjc);
                        ps.setBigDecimal(col++, BigDecimal.valueOf(vlLodo));
                        ps.setNull      (col++, Types.SMALLINT);  // CD_TIP_NTZ_MVTC nullable
                        ps.setShort     (col++, estRms);
                        ps.setInt       (col++, qtdReg);

                        ps.addBatch();

                        // Commit a cada 1.000 linhas para não estourar o log do DB2
                        if (id % 1_000 == 0) {
                            ps.executeBatch();
                            conn.commit();
                        }
                    }
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        LOG.infof("[SEED] %d remessas inseridas (1/agente/fundo/dia desde 2024-01-01).", id - 1);
    }

    private void preencherLinha(PreparedStatement ps, long id, ThreadLocalRandom rnd) throws SQLException {
        // Datas: 40% Abr-2026 (mês atual p/ warm-up), 60% Jan-2020 a Mar-2026
        LocalDate dtFrmz = gerarData(rnd);
        LocalDate dtVnct = dtFrmz.plusYears(3 + rnd.nextInt(8)); // 3-10 anos de prazo

        // Valores financeiros
        double vlOpr = Math.round((10_000 + rnd.nextDouble() * 1_990_000) * 100.0) / 100.0;
        double vlFatm = Math.round(vlOpr * (1.2 + rnd.nextDouble() * 2.0) * 100.0) / 100.0;
        double pcGrt = 80.0;
        double vlSdoCptlNmld = Math.round(vlOpr * (0.1 + rnd.nextDouble() * 0.9) * 100.0) / 100.0;
        boolean inadimplente = rnd.nextInt(10) < 3; // 30% inadimplentes
        double vlSdoCptlAtr = inadimplente ? Math.round(vlSdoCptlNmld * (0.1 + rnd.nextDouble() * 0.9) * 100.0) / 100.0 : 0.0;
        double vlSdoEncgNmld = Math.round(vlSdoCptlNmld * 0.065 * 100.0) / 100.0;
        double vlSdoEncgAtr = inadimplente ? Math.round(vlSdoCptlAtr * 0.065 * 100.0) / 100.0 : 0.0;
        double vlGrtOprAjsd = Math.round(vlSdoCptlNmld * pcGrt / 100.0 * 100.0) / 100.0;

        short cdTipEstOpr;
        if (inadimplente) {
            cdTipEstOpr = 3; // Inadimplente
        } else {
            int r = rnd.nextInt(10);
            cdTipEstOpr = (short) (r < 6 ? 1 : r < 8 ? 2 : r < 9 ? 4 : 5); // Ativa, Encerrada, Liquidada, Honrada
        }

        int agente = AGENTES[rnd.nextInt(AGENTES.length)];

        // CHAR(20) para CD_IDFR_EXNO_OPR
        String extRef = String.format("%-20s", "EXT" + agente + "-" + id).substring(0, 20);
        // CHAR(2) para CD_NVL_RSCO_OPR
        String nvlRisco = NIVEIS_RISCO[rnd.nextInt(NIVEIS_RISCO.length)]; // já tem 2 chars

        int col = 1;
        ps.setInt   (col++, (int)(id % Integer.MAX_VALUE + 1));                // CD_OPR_CRD_FNDO
        ps.setInt   (col++, FUNDOS[rnd.nextInt(FUNDOS.length)]);               // CD_FNDO_GRTR
        ps.setInt   (col++, agente);                                            // CD_AGT_FNCO
        ps.setString(col++, extRef);                                            // CD_IDFR_EXNO_OPR CHAR(20)
        ps.setShort (col++, MODALIDS[rnd.nextInt(MODALIDS.length)]);           // CD_TIP_MDLD_CRD
        ps.setShort (col++, FINALIDS[rnd.nextInt(FINALIDS.length)]);           // CD_TIP_FNLD_CRD
        ps.setShort (col++, TIPOS_PSS[rnd.nextInt(TIPOS_PSS.length)]);        // CD_TIP_PSS
        ps.setShort (col++, (short)(1 + rnd.nextInt(4)));                      // NR_AG_CTRT_OPR
        ps.setInt   (col++, IBGE_MUNS[rnd.nextInt(IBGE_MUNS.length)]);        // CD_IBGE_MUN_AG
        ps.setBigDecimal(col++, BigDecimal.valueOf(rnd.nextLong(10_000_000_000_000L, 99_999_999_999_999L))); // CD_IDFR_SRF DECIMAL(14,0)
        ps.setNull  (col++, Types.INTEGER);                                    // CD_RADC_CNPJ nullable
        ps.setShort (col++, PUBLICOS[rnd.nextInt(PUBLICOS.length)]);           // CD_TIP_PBCO_ALVO
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlFatm));                   // VL_FATM_BRTO_AAL
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlOpr));                    // VL_OPR_CRD
        ps.setBigDecimal(col++, BigDecimal.valueOf(pcGrt));                    // PC_GRT_OPR_CRD
        ps.setShort (col++, CONDICOES[rnd.nextInt(CONDICOES.length)]);        // CD_TIP_CND_ESPL
        ps.setDate  (col++, Date.valueOf(dtFrmz));                             // DT_FRMZ_OPR
        ps.setDate  (col++, Date.valueOf(dtVnct));                             // DT_VNCT_OPR
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoCptlNmld));            // VL_SDO_CPTL_NMLD
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoCptlAtr));             // VL_SDO_CPTL_ATR
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoEncgNmld));            // VL_SDO_ENCG_NMLD
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoEncgAtr));             // VL_SDO_ENCG_ATR
        ps.setString(col++, nvlRisco);                                         // CD_NVL_RSCO_OPR CHAR(2)
        ps.setNull  (col++, Types.DATE);                                       // DT_SDO_OPR nullable
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlGrtOprAjsd));             // VL_GRT_OPR_AJSD
        ps.setBigDecimal(col++, BigDecimal.valueOf(vlOpr));                    // VL_TTL_LIBD_OPR
        if (inadimplente) {
            ps.setDate(col++, Date.valueOf(dtFrmz.plusMonths(1 + rnd.nextInt(12))));  // DT_PRMO_SDO_ATR
        } else {
            ps.setNull(col++, Types.DATE);
        }
        ps.setShort (col++, CRNG_AMTZ[rnd.nextInt(CRNG_AMTZ.length)]);       // CD_TIP_CRNG_AMTZ
        ps.setShort (col++, cdTipEstOpr);                                      // CD_TIP_EST_OPR
        ps.setNull  (col++, Types.DATE);                                       // DT_DPC_EXNO_OPR nullable
        ps.setNull  (col++, Types.DECIMAL);                                    // IC_FTR_CMSS nullable
        ps.setShort (col++, FONTES[rnd.nextInt(FONTES.length)]);               // CD_TIP_FON_RCS
        ps.setShort (col++, PROGRAMAS[rnd.nextInt(PROGRAMAS.length)]);         // CD_TIP_PGM_CRD
        ps.setShort (col++, TIPOS_FRMZ[rnd.nextInt(TIPOS_FRMZ.length)]);      // CD_TIP_FRMZ
        ps.setShort (col++, (short) 1);                                        // CD_ENDO_GRTR
        ps.setString(col++, "S");                                              // IN_FATM_VLDD_SRF CHAR(1)
        ps.setString(col++, "S");                                              // IN_OPR_CADD_BC CHAR(1)
        ps.setNull  (col++, Types.DECIMAL);                                    // VL_SBS_CRD nullable
        ps.setNull  (col++, Types.SMALLINT);                                   // CD_SEXO_QLF_OPR nullable
        ps.setNull  (col++, Types.DECIMAL);                                    // NR_CPF_QLF_OPR nullable
        ps.setNull  (col++, Types.DECIMAL);                                    // IC_PDA_CRD nullable
        ps.setShort (col++, (short)(1 + rnd.nextInt(5)));                      // CD_TIP_RGAO
        ps.setNull  (col++, Types.CHAR);                                       // CD_BC_OPR_CRD nullable
        ps.setInt   (col++, 1 + rnd.nextInt(3));                               // NR_SEQL_CT_MVT
        ps.setNull  (col++, Types.DECIMAL);                                    // NR_CPF_MTR nullable
        ps.setNull  (col++, Types.CHAR);                                       // CD_CNPJ_MTR nullable
        ps.setNull  (col++, Types.CHAR);                                       // CD_RADC_CNPJ_MTR nullable
    }

    /**
     * Distribuição de datas:
     *  - 35% Abril/2026 (mês atual — garante dados no warm-up)
     *  - 15% Jan-Mar/2026
     *  - 50% Jan/2020 a Dez/2025 (histórico variado)
     */
    private LocalDate gerarData(ThreadLocalRandom rnd) {
        int choice = rnd.nextInt(20);
        if (choice < 7) {
            // Abril/2026
            return LocalDate.of(2026, 4, 1 + rnd.nextInt(18));
        } else if (choice < 10) {
            // Jan-Mar/2026
            int mes = 1 + rnd.nextInt(3);
            return LocalDate.of(2026, mes, 1 + rnd.nextInt(LocalDate.of(2026, mes, 1).lengthOfMonth()));
        } else {
            // 2020-2025 (histórico)
            int ano = 2020 + rnd.nextInt(6);
            int mes = 1 + rnd.nextInt(12);
            int dia = 1 + rnd.nextInt(LocalDate.of(ano, mes, 1).lengthOfMonth());
            return LocalDate.of(ano, mes, dia);
        }
    }
}
