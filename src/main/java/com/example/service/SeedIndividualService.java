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

/**
 * Inserts unitários por tabela — complementa o SeedService (mass seed).
 * Cada método abre sua própria conexão, comita e fecha.
 */
@ApplicationScoped
public class SeedIndividualService {

    private static final Logger LOG = Logger.getLogger(SeedIndividualService.class);

    @Inject
    AgroalDataSource dataSource;

    // =========================================================================
    // AGENTE (AGT_FNCO + associação AGT_FNCO_FNDO_GRTR em todos os fundos)
    // =========================================================================

    /**
     * Insere um agente financeiro e o associa a todos os fundos já existentes.
     *
     * @param codAgente código único do agente (PK)
     * @param nome      nome abreviado (max 60 chars)
     * @param ispb      código ISPB de 8 dígitos (padrão "00000000")
     * @param codCli    código do cliente interno (padrão = 1000 + codAgente)
     */
    public void inserirAgente(int codAgente, String nome, String ispb, Integer codCli) throws SQLException {
        if (ispb == null || ispb.isBlank()) ispb = "00000000";
        if (codCli == null) codCli = 1000 + codAgente;
        String nomePad = String.format("%-60s", nome).substring(0, 60);
        String ispbFmt = String.format("%-8s", ispb).substring(0, 8);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO DB2GFG.AGT_FNCO (CD_AGT_FNCO, CD_CLI, NM_ABVD_AGT_FNCO) VALUES ("
                        + codAgente + ", " + codCli + ", '" + nomePad + "')");
                conn.commit();

                // Associa ao agente em todos os fundos existentes
                try (ResultSet rs = st.executeQuery("SELECT CD_FNDO_GRTR FROM DB2GFG.FNDO_GRTR ORDER BY CD_FNDO_GRTR")) {
                    while (rs.next()) {
                        int fundo = rs.getInt(1);
                        st.execute("INSERT INTO DB2GFG.AGT_FNCO_FNDO_GRTR "
                                + "(CD_FNDO_GRTR, CD_AGT_FNCO, CD_TIP_EST_AGT, CD_TIP_ITCB_FNCO, "
                                + " CD_ISPB, NR_CTR_ITCB_DADO, CD_CLI, CD_PRD, "
                                + " NR_CT_DEP_AGT, NR_AG_CT_DEP_AGT, CD_USU_RSP_VLDC) VALUES ("
                                + fundo + ", " + codAgente + ", 1, 1, '" + ispbFmt + "', "
                                + (fundo * 1000 + codAgente) + ", " + codCli + ", 1, "
                                + (100000000 + codAgente * 1000000 + fundo * 100) + ", "
                                + codCli + ", 'USRVLDC')");
                    }
                }
                conn.commit();
            }
        }
        LOG.infof("[SEED-IND] Agente %d (%s) inserido.", codAgente, nome.trim());
    }

    // =========================================================================
    // FUNDO (FNDO_GRTR + associação com todos os agentes existentes)
    // =========================================================================

    /**
     * Insere um fundo garantidor e o associa a todos os agentes já existentes.
     *
     * @param codFundo código único do fundo (PK)
     * @param sigla    sigla do fundo (ex: "FGO")
     * @param nome     nome completo do fundo
     */
    public void inserirFundo(int codFundo, String sigla, String nome) throws SQLException {
        LocalDate hoje = LocalDate.now();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO DB2GFG.FNDO_GRTR "
                        + "(CD_FNDO_GRTR, SG_FNDO_GRTR, NM_FNDO_GRTR, NR_AG_CT_MVT_FNDO, NR_CT_MVT_FNDO, "
                        + " CD_CIA_CTB, IDT_ULT_FCHT_BAL, DT_INC_FNDO_GRTR, DT_ECR_FNDO_GRTR, "
                        + " CD_TIP_EST_ENDO, CD_USU_RSP_ATL_REG, CD_USU_RSP_VLDC, TS_ATL_REG) VALUES ("
                        + codFundo + ", '" + sigla + "', '" + nome + "', 1001, "
                        + (100000000 + codFundo) + ", 'BB1', '" + hoje + "', '"
                        + hoje + "', '2099-12-31', 1, 'USRSIST', 'USRVLDC', '"
                        + LocalDateTime.now().withNano(0) + "')");
                conn.commit();

                // Associa ao fundo em todos os agentes existentes
                try (ResultSet rs = st.executeQuery(
                        "SELECT CD_AGT_FNCO, CD_CLI FROM DB2GFG.AGT_FNCO ORDER BY CD_AGT_FNCO")) {
                    while (rs.next()) {
                        int ag = rs.getInt(1);
                        int cli = rs.getInt(2);
                        st.execute("INSERT INTO DB2GFG.AGT_FNCO_FNDO_GRTR "
                                + "(CD_FNDO_GRTR, CD_AGT_FNCO, CD_TIP_EST_AGT, CD_TIP_ITCB_FNCO, "
                                + " CD_ISPB, NR_CTR_ITCB_DADO, CD_CLI, CD_PRD, "
                                + " NR_CT_DEP_AGT, NR_AG_CT_DEP_AGT, CD_USU_RSP_VLDC) VALUES ("
                                + codFundo + ", " + ag + ", 1, 1, '00000000', "
                                + (codFundo * 1000 + ag) + ", " + cli + ", 1, "
                                + (100000000 + ag * 1000000 + codFundo * 100) + ", "
                                + cli + ", 'USRVLDC')");
                    }
                }
                conn.commit();
            }
        }
        LOG.infof("[SEED-IND] Fundo %d (%s) inserido.", codFundo, sigla);
    }

    // =========================================================================
    // PROGRAMA DE CRÉDITO (TIP_PGM_CRD)
    // =========================================================================

    /**
     * Insere um programa de crédito.
     *
     * @param codPrograma código único (PK)
     * @param nome        nome do programa
     */
    public void inserirPrograma(int codPrograma, String nome) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO DB2GFG.TIP_PGM_CRD VALUES (" + codPrograma + ", '" + nome + "')");
        }
        LOG.infof("[SEED-IND] Programa %d (%s) inserido.", codPrograma, nome);
    }

    // =========================================================================
    // OPERAÇÃO INDIVIDUAL (OPR_CRD_FNDO_GRTR)
    // =========================================================================

    /**
     * Insere uma operação de crédito. Campos não informados recebem valores sintéticos.
     *
     * @param codAgente        código do agente (obrigatório)
     * @param codFundo         código do fundo (padrão 1)
     * @param codPrograma      código do programa de crédito (padrão aleatório 1-6)
     * @param vlrOperacao      valor do crédito em R$ (padrão aleatório 10k-2M)
     * @param dataFormalizacao data de formalização (padrão hoje)
     * @param tipoPessoa       1=PF, 2=PJ, 3=Produtor Rural (padrão 1)
     * @param cpfCnpj          CPF/CNPJ numérico (padrão aleatório)
     * @return id gerado para a operação
     */
    public long inserirOperacao(int codAgente, Integer codFundo, Integer codPrograma,
                                BigDecimal vlrOperacao, LocalDate dataFormalizacao,
                                Integer tipoPessoa, BigDecimal cpfCnpj) throws SQLException {

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (codFundo == null)         codFundo = 1;
        if (codPrograma == null)      codPrograma = 1 + rnd.nextInt(6);
        if (dataFormalizacao == null) dataFormalizacao = LocalDate.now();
        if (tipoPessoa == null)       tipoPessoa = 1;

        double vlOpr = vlrOperacao != null
                ? vlrOperacao.doubleValue()
                : Math.round((10_000 + rnd.nextDouble() * 1_990_000) * 100.0) / 100.0;

        double vlFatm          = Math.round(vlOpr * (1.2 + rnd.nextDouble() * 2.0) * 100.0) / 100.0;
        double vlSdoCptlNmld   = Math.round(vlOpr * (0.5 + rnd.nextDouble() * 0.5) * 100.0) / 100.0;
        boolean inad           = rnd.nextInt(10) < 2; // 20% inadimplentes
        double vlSdoCptlAtr    = inad ? Math.round(vlSdoCptlNmld * 0.3 * 100.0) / 100.0 : 0.0;
        double vlSdoEncgNmld   = Math.round(vlSdoCptlNmld * 0.065 * 100.0) / 100.0;
        double vlSdoEncgAtr    = inad ? Math.round(vlSdoCptlAtr * 0.065 * 100.0) / 100.0 : 0.0;
        double vlGrtOprAjsd    = Math.round(vlSdoCptlNmld * 0.8 * 100.0) / 100.0;
        short  estOpr          = inad ? (short) 3 : (short) (rnd.nextInt(10) < 6 ? 1 : 2);
        LocalDate dtVnct       = dataFormalizacao.plusYears(5 + rnd.nextInt(5));
        BigDecimal cpf         = cpfCnpj != null
                ? cpfCnpj
                : BigDecimal.valueOf(rnd.nextLong(10_000_000_000_000L, 99_999_999_999_999L));

        // Obtém próximo ID
        long novoId;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(CD_OPR_CRD_FNDO), 0) FROM DB2GFG.OPR_CRD_FNDO_GRTR")) {
            rs.next();
            novoId = rs.getLong(1) + 1;
        }

        String extRef = String.format("%-20s", "EXT" + codAgente + "-" + novoId).substring(0, 20);
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int col = 1;
            ps.setLong   (col++, novoId);
            ps.setInt    (col++, codFundo);
            ps.setInt    (col++, codAgente);
            ps.setString (col++, extRef);
            ps.setShort  (col++, (short)(1 + rnd.nextInt(6)));   // CD_TIP_MDLD_CRD
            ps.setShort  (col++, (short)(1 + rnd.nextInt(6)));   // CD_TIP_FNLD_CRD
            ps.setShort  (col++, tipoPessoa.shortValue());        // CD_TIP_PSS
            ps.setShort  (col++, (short)(1 + rnd.nextInt(4)));   // NR_AG_CTRT_OPR
            ps.setInt    (col++, 5300108);                        // CD_IBGE_MUN_AG (Brasília)
            ps.setBigDecimal(col++, cpf);                         // CD_IDFR_SRF
            ps.setNull   (col++, Types.INTEGER);                  // CD_RADC_CNPJ
            ps.setShort  (col++, (short)(1 + rnd.nextInt(5)));   // CD_TIP_PBCO_ALVO
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlFatm));  // VL_FATM_BRTO_AAL
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlOpr));   // VL_OPR_CRD
            ps.setBigDecimal(col++, BigDecimal.valueOf(80.0));    // PC_GRT_OPR_CRD
            ps.setShort  (col++, (short)(1 + rnd.nextInt(4)));   // CD_TIP_CND_ESPL
            ps.setDate   (col++, Date.valueOf(dataFormalizacao)); // DT_FRMZ_OPR
            ps.setDate   (col++, Date.valueOf(dtVnct));           // DT_VNCT_OPR
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoCptlNmld));
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoCptlAtr));
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoEncgNmld));
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlSdoEncgAtr));
            ps.setString (col++, "A1");                           // CD_NVL_RSCO_OPR CHAR(2)
            ps.setNull   (col++, Types.DATE);                     // DT_SDO_OPR
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlGrtOprAjsd));
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlOpr));   // VL_TTL_LIBD_OPR
            if (inad) ps.setDate(col++, Date.valueOf(dataFormalizacao.plusMonths(3)));
            else       ps.setNull(col++, Types.DATE);             // DT_PRMO_SDO_ATR
            ps.setShort  (col++, (short)(1 + rnd.nextInt(3)));   // CD_TIP_CRNG_AMTZ
            ps.setShort  (col++, estOpr);                         // CD_TIP_EST_OPR
            ps.setNull   (col++, Types.DATE);                     // DT_DPC_EXNO_OPR
            ps.setNull   (col++, Types.DECIMAL);                  // IC_FTR_CMSS
            ps.setShort  (col++, (short)(1 + rnd.nextInt(4)));   // CD_TIP_FON_RCS
            ps.setShort  (col++, codPrograma.shortValue());       // CD_TIP_PGM_CRD
            ps.setShort  (col++, (short)(1 + rnd.nextInt(3)));   // CD_TIP_FRMZ
            ps.setShort  (col++, (short) 1);                      // CD_ENDO_GRTR
            ps.setString (col++, "S");                            // IN_FATM_VLDD_SRF
            ps.setString (col++, "S");                            // IN_OPR_CADD_BC
            ps.setNull   (col++, Types.DECIMAL);                  // VL_SBS_CRD
            ps.setNull   (col++, Types.SMALLINT);                 // CD_SEXO_QLF_OPR
            ps.setNull   (col++, Types.DECIMAL);                  // NR_CPF_QLF_OPR
            ps.setNull   (col++, Types.DECIMAL);                  // IC_PDA_CRD
            ps.setShort  (col++, (short)(1 + rnd.nextInt(5)));   // CD_TIP_RGAO
            ps.setNull   (col++, Types.CHAR);                     // CD_BC_OPR_CRD
            ps.setInt    (col++, 1 + rnd.nextInt(3));             // NR_SEQL_CT_MVT
            ps.setNull   (col++, Types.DECIMAL);                  // NR_CPF_MTR
            ps.setNull   (col++, Types.CHAR);                     // CD_CNPJ_MTR
            ps.setNull   (col++, Types.CHAR);                     // CD_RADC_CNPJ_MTR
            ps.executeUpdate();
        }
        LOG.infof("[SEED-IND] Operação id=%d inserida (agente=%d, fundo=%d, vlr=%.2f).",
                novoId, codAgente, codFundo, vlOpr);
        return novoId;
    }

    // =========================================================================
    // REMESSA INDIVIDUAL (RMS_AGT_FNCO)
    // =========================================================================

    /**
     * Insere uma remessa individual para um agente/fundo/data.
     *
     * @param codAgente código do agente
     * @param codFundo  código do fundo (padrão 1)
     * @param data      data de referência da remessa (padrão hoje)
     * @param qtdReg    quantidade de registros no lote (padrão aleatório 10k-50k)
     * @return id gerado para a remessa
     */
    public long inserirRemessa(int codAgente, Integer codFundo, LocalDate data, Integer qtdReg) throws SQLException {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (codFundo == null) codFundo = 1;
        if (data == null)     data = LocalDate.now();
        if (qtdReg == null)   qtdReg = 10_000 + rnd.nextInt(40_000);

        long novoId;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(CD_RMS_AGT_FNCO), 0) FROM DB2GFG.RMS_AGT_FNCO")) {
            rs.next();
            novoId = rs.getLong(1) + 1;
        }

        String nmDtst = String.format("%-44s",
                String.format("AGT%03d_F%d_%s.txt", codAgente, codFundo, data.toString().replace("-", "")))
                .substring(0, 44);
        double vlLodo = Math.round(qtdReg * (50_000 + rnd.nextDouble() * 450_000) * 100.0) / 100.0;
        LocalDateTime tsRct = data.atTime(7 + rnd.nextInt(3), rnd.nextInt(60));

        String sql = """
            INSERT INTO DB2GFG.RMS_AGT_FNCO (
              CD_RMS_AGT_FNCO, CD_FNDO_GRTR, CD_AGT_FNCO, NR_SEQL_RMS, NM_DTST,
              DT_VRS_LAUT, TS_RCT_RMS, DT_PRCT_EVT, DT_CTC_UTZD_PRCT,
              DT_ATL_MNTR, DT_MVTC_FNCR, CD_MTV_RJC_RMS, VL_LODO_MVTC_RMS,
              CD_TIP_NTZ_MVTC, CD_TIP_EST_RMS, QT_REG_RMS
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int col = 1;
            ps.setLong     (col++, novoId);
            ps.setInt      (col++, codFundo);
            ps.setInt      (col++, codAgente);
            ps.setShort    (col++, (short) 1);
            ps.setString   (col++, nmDtst);
            ps.setDate     (col++, Date.valueOf(data));
            ps.setTimestamp(col++, Timestamp.valueOf(tsRct));
            ps.setNull     (col++, Types.DATE);    // DT_PRCT_EVT
            ps.setNull     (col++, Types.DATE);    // DT_CTC_UTZD_PRCT
            ps.setNull     (col++, Types.DATE);    // DT_ATL_MNTR
            ps.setNull     (col++, Types.DATE);    // DT_MVTC_FNCR
            ps.setShort    (col++, (short) 0);     // CD_MTV_RJC_RMS
            ps.setBigDecimal(col++, BigDecimal.valueOf(vlLodo));
            ps.setNull     (col++, Types.SMALLINT);// CD_TIP_NTZ_MVTC
            ps.setShort    (col++, (short) 1);     // CD_TIP_EST_RMS = Recebida
            ps.setInt      (col++, qtdReg);
            ps.executeUpdate();
        }
        LOG.infof("[SEED-IND] Remessa id=%d inserida (agente=%d, fundo=%d, data=%s).",
                novoId, codAgente, codFundo, data);
        return novoId;
    }
}
