package com.example.service;

import com.example.dto.SistemaDto;
import com.example.dto.SistemaDto.*;
import io.agroal.api.AgroalDataSource;
import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coleta métricas completas do sistema: servidor, JVM, Redis e DB2.
 */
@ApplicationScoped
public class SistemaService {

    private static final Logger LOG = Logger.getLogger(SistemaService.class);
    private static final double GB = 1024.0 * 1024.0 * 1024.0;
    private static final double MB = 1024.0 * 1024.0;

    @Inject
    RedisClient redisClient;

    @Inject
    RedisDataSource redis;

    @Inject
    AgroalDataSource dataSource;

    public SistemaDto coletar() {
        return new SistemaDto(
                coletarServidor(),
                coletarJvm(),
                coletarRedis(),
                coletarDb2(),
                LocalDateTime.now()
        );
    }

    // =========================================================================
    // SERVIDOR (OS)
    // =========================================================================

    private ServidorDto coletarServidor() {
        ServidorDto dto = new ServidorDto();

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        dto.setOs(os.getName() + " " + os.getVersion());
        dto.setArquitetura(os.getArch());
        dto.setCpus(os.getAvailableProcessors());
        dto.setLoadAvg1m(round2(os.getSystemLoadAverage()));
        dto.setUptimeMs(ManagementFactory.getRuntimeMXBean().getUptime());

        try {
            dto.setHostname(java.net.InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            dto.setHostname("desconhecido");
        }

        // Memória física via com.sun.management.OperatingSystemMXBean (disponível no OpenJDK)
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            long total = sunOs.getTotalMemorySize();
            long livre = sunOs.getFreeMemorySize();
            long usado = total - livre;
            dto.setMemoriaFisicaTotalGb(round2(total / GB));
            dto.setMemoriaFisicaLivreGb(round2(livre / GB));
            dto.setMemoriaFisicaUsadaGb(round2(usado / GB));
            dto.setMemoriaFisicaUsadaPct(total > 0 ? round2(100.0 * usado / total) : 0);
        } else {
            // Fallback: lê /proc/meminfo (Linux)
            dto.setMemoriaFisicaTotalGb(-1);
            dto.setMemoriaFisicaLivreGb(-1);
            dto.setMemoriaFisicaUsadaGb(-1);
            dto.setMemoriaFisicaUsadaPct(-1);
        }

        // Discos
        List<DiscoDto> discos = new ArrayList<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total == 0) continue;
            long livre = root.getFreeSpace();
            long usado = total - livre;
            discos.add(new DiscoDto(
                    root.getAbsolutePath(),
                    round2(total / GB),
                    round2(livre / GB),
                    round2(usado / GB),
                    round2(100.0 * usado / total)
            ));
        }
        dto.setDiscos(discos);
        return dto;
    }

    // =========================================================================
    // JVM
    // =========================================================================

    private JvmDto coletarJvm() {
        JvmDto dto = new JvmDto();

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long heapUsado = mem.getHeapMemoryUsage().getUsed();
        long heapMax   = mem.getHeapMemoryUsage().getMax();
        long nonHeap   = mem.getNonHeapMemoryUsage().getUsed();

        dto.setVersao(System.getProperty("java.version", "?"));
        dto.setHeapUsadoMb(round2(heapUsado / MB));
        dto.setHeapMaxMb(round2(heapMax / MB));
        dto.setHeapUsadoPct(heapMax > 0 ? round2(100.0 * heapUsado / heapMax) : 0);
        dto.setNonHeapUsadoMb(round2(nonHeap / MB));
        dto.setUptimeMs(ManagementFactory.getRuntimeMXBean().getUptime());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        dto.setThreadsAtivos(threads.getThreadCount());
        dto.setThreadsTotal((int) threads.getTotalStartedThreadCount());

        return dto;
    }

    // =========================================================================
    // REDIS — INFO command (parseia o output multi-linha chave:valor)
    // =========================================================================

    private RedisDto coletarRedis() {
        RedisDto dto = new RedisDto();
        try {
            String info = redisClient.info(java.util.List.of()).toString();
            Map<String, String> kv = parseRedisInfo(info);

            dto.setVersao(kv.getOrDefault("redis_version", "?"));
            dto.setModo(kv.getOrDefault("redis_mode", "?"));
            dto.setUptimeServidor(formatUptime(Long.parseLong(kv.getOrDefault("uptime_in_seconds", "0"))));
            dto.setClientesConectados(parseLong(kv, "connected_clients"));

            // Memória
            dto.setMemoriaUsadaMb(round2(parseLong(kv, "used_memory") / MB));
            dto.setMemoriaUsadaPicoMb(round2(parseLong(kv, "used_memory_peak") / MB));
            dto.setMemoriaDisponiveisystemMb(round2(parseLong(kv, "total_system_memory") / MB));

            // Stats
            dto.setTotalComandosProcessados(parseLong(kv, "total_commands_processed"));
            long hits   = parseLong(kv, "keyspace_hits");
            long misses = parseLong(kv, "keyspace_misses");
            dto.setHitsCache(hits);
            dto.setMissesCache(misses);
            long total = hits + misses;
            dto.setHitRatioPct(total > 0 ? round2(100.0 * hits / total) : 0);

            // Keyspace: conta chaves em todos os DBs
            long totalChaves = 0;
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (e.getKey().startsWith("db")) {
                    // formato: keys=14,expires=7,avg_ttl=...
                    String v = e.getValue();
                    int idx = v.indexOf("keys=");
                    if (idx >= 0) {
                        int end = v.indexOf(',', idx);
                        String num = end > 0 ? v.substring(idx + 5, end) : v.substring(idx + 5);
                        totalChaves += Long.parseLong(num.trim());
                    }
                }
            }
            dto.setTotalChaves(totalChaves);

        } catch (Exception e) {
            LOG.warnf("[SISTEMA] Falha ao coletar INFO do Redis: %s", e.getMessage());
            dto.setErro(e.getMessage());
        }
        return dto;
    }

    private Map<String, String> parseRedisInfo(String info) {
        Map<String, String> map = new HashMap<>();
        for (String linha : info.split("\r?\n")) {
            if (linha.startsWith("#") || linha.isBlank()) continue;
            int sep = linha.indexOf(':');
            if (sep > 0) {
                map.put(linha.substring(0, sep).trim(), linha.substring(sep + 1).trim());
            }
        }
        return map;
    }

    private String formatUptime(long segundos) {
        long dias   = segundos / 86400;
        long horas  = (segundos % 86400) / 3600;
        long minutos = (segundos % 3600) / 60;
        return String.format("%dd %02dh %02dm", dias, horas, minutos);
    }

    // =========================================================================
    // DB2 — SYSCAT.TABLES com tamanho real das tabelas
    // =========================================================================

    private Db2Dto coletarDb2() {
        Db2Dto dto = new Db2Dto();
        List<TabelaDb2Dto> tabelas = new ArrayList<>();

        String sql = """
            SELECT T.TABNAME, T.TABSCHEMA,
                   COALESCE(T.CARD, -1)   AS CARD,
                   COALESCE(T.FPAGES, 0)  AS FPAGES,
                   COALESCE(TS.PAGESIZE, 8192) AS PAGESIZE,
                   T.STATUS
            FROM SYSCAT.TABLES T
            LEFT JOIN SYSCAT.TABLESPACES TS ON T.TBSPACE = TS.TBSPACE
            WHERE T.TABSCHEMA = 'DB2GFG'
              AND T.TYPE = 'T'
            ORDER BY FPAGES DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                tabelas.add(new TabelaDb2Dto(
                        rs.getString("TABNAME").trim(),
                        rs.getString("TABSCHEMA").trim(),
                        rs.getLong("CARD"),
                        rs.getLong("FPAGES"),
                        rs.getInt("PAGESIZE"),
                        rs.getString("STATUS")
                ));
            }
            dto.setTabelas(tabelas);

        } catch (Exception e) {
            LOG.warnf("[SISTEMA] Falha ao consultar SYSCAT.TABLES: %s", e.getMessage());
            dto.setErro(e.getMessage());
            dto.setTabelas(List.of());
        }
        return dto;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    private long parseLong(Map<String, String> kv, String key) {
        try { return Long.parseLong(kv.getOrDefault(key, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
