package com.example.service;

import com.example.dto.SistemaDto;
import com.example.dto.SistemaDto.*;
import io.agroal.api.AgroalDataSource;
import io.quarkus.redis.client.RedisClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SistemaService {

    private static final Logger LOG = Logger.getLogger(SistemaService.class);
    private static final double GB  = 1024.0 * 1024.0 * 1024.0;
    private static final double MB  = 1024.0 * 1024.0;

    @Inject RedisClient redisClient;
    @Inject AgroalDataSource dataSource;

    public SistemaDto coletar() {
        // RAM física do servidor — base para % de todos os componentes
        long ramFisicaBytes = ramFisicaTotal();
        double ramFisicaGb  = round2(ramFisicaBytes / GB);

        return new SistemaDto(
                coletarServidor(ramFisicaBytes),
                coletarJvm(ramFisicaBytes),
                coletarRedis(ramFisicaBytes),
                coletarDb2(),
                coletarProcessos(ramFisicaBytes, ramFisicaGb),
                LocalDateTime.now()
        );
    }

    // =========================================================================
    // SERVIDOR
    // =========================================================================

    private ServidorDto coletarServidor(long ramFisicaBytes) {
        ServidorDto dto = new ServidorDto();

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        dto.setOs(os.getName() + " " + os.getVersion());
        dto.setArquitetura(os.getArch());
        dto.setCpus(os.getAvailableProcessors());
        dto.setLoadAvg1m(round2(os.getSystemLoadAverage()));
        dto.setUptimeMs(ManagementFactory.getRuntimeMXBean().getUptime());

        try { dto.setHostname(java.net.InetAddress.getLocalHost().getHostName()); }
        catch (Exception e) { dto.setHostname("desconhecido"); }

        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            long total  = sunOs.getTotalMemorySize();
            long livre  = sunOs.getFreeMemorySize();
            // Cache/buffers do kernel não contam como "em uso" de verdade
            long cached = lerMeminfokB("Cached") * 1024L + lerMeminfokB("Buffers") * 1024L;
            long usadoReal = total - livre - cached;
            dto.setMemoriaFisicaTotalGb(round2(total / GB));
            dto.setMemoriaFisicaLivreGb(round2(livre / GB));
            dto.setMemoriaFisicaUsadaGb(round2(usadoReal / GB));
            dto.setMemoriaFisicaCachedGb(round2(cached / GB));
            dto.setMemoriaFisicaUsadaPct(total > 0 ? round2(100.0 * usadoReal / total) : 0);
            dto.setMemoriaFisicaDisponiveGb(round2(lerMeminfokB("MemAvailable") * 1024L / GB));
        }

        // Discos reais (filtra overlay/tmpfs/shm)
        List<DiscoDto> discos = new ArrayList<>();
        try {
            java.nio.file.FileSystem fs = java.nio.file.FileSystems.getDefault();
            for (java.nio.file.FileStore store : fs.getFileStores()) {
                String type = store.type();
                if (type.equals("tmpfs") || type.equals("overlay") || type.equals("shm")
                        || type.equals("devtmpfs") || type.equals("cgroup2")) continue;
                long total = store.getTotalSpace();
                if (total == 0) continue;
                long usado = total - store.getUsableSpace();
                discos.add(new DiscoDto(
                        store.name() + " → " + store.toString().replaceAll(" \\(.*\\)$", ""),
                        type,
                        round2(total / GB),
                        round2(store.getUsableSpace() / GB),
                        round2(usado / GB),
                        round2(100.0 * usado / total)
                ));
            }
        } catch (Exception e) {
            // fallback para File.listRoots()
            for (File root : File.listRoots()) {
                long total = root.getTotalSpace();
                if (total == 0) continue;
                long livre = root.getFreeSpace();
                discos.add(new DiscoDto(root.getAbsolutePath(), "?",
                        round2(total / GB), round2(livre / GB),
                        round2((total - livre) / GB),
                        round2(100.0 * (total - livre) / total)));
            }
        }
        dto.setDiscos(discos);
        return dto;
    }

    // =========================================================================
    // JVM
    // =========================================================================

    private JvmDto coletarJvm(long ramFisicaBytes) {
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

        // RSS real do processo (VmRSS = memoria física ocupada na RAM, não virtual)
        double rssMb = lerVmRss("/proc/self/status");
        dto.setRssMb(rssMb);
        dto.setRssDoSistemaPct(ramFisicaBytes > 0 ? round2(100.0 * rssMb * MB / ramFisicaBytes) : 0);

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        dto.setThreadsAtivos(threads.getThreadCount());
        dto.setThreadsTotal((int) threads.getTotalStartedThreadCount());
        dto.setUptimeMs(ManagementFactory.getRuntimeMXBean().getUptime());

        // Pool de conexões DB2
        try {
            var metrics = dataSource.getMetrics();
            dto.setDb2ConexoesAtivas((int) metrics.activeCount());
            dto.setDb2ConexoesMax(dataSource.getConfiguration()
                    .connectionPoolConfiguration().maxSize());
            dto.setDb2TotalCriadas(metrics.creationCount());
        } catch (Exception e) {
            LOG.debugf("[SISTEMA] Métricas Agroal: %s", e.getMessage());
        }

        return dto;
    }

    // =========================================================================
    // REDIS
    // =========================================================================

    private RedisDto coletarRedis(long ramFisicaBytes) {
        RedisDto dto = new RedisDto();
        try {
            String info = redisClient.info(java.util.List.of()).toString();
            Map<String, String> kv = parseRedisInfo(info);

            dto.setVersao(kv.getOrDefault("redis_version", "?"));
            dto.setModo(kv.getOrDefault("redis_mode", "?"));
            dto.setPapel(kv.getOrDefault("role", "?"));
            dto.setUptimeServidor(formatUptime(parseLong(kv, "uptime_in_seconds")));
            dto.setClientesConectados(parseLong(kv, "connected_clients"));

            long usedBytes = parseLong(kv, "used_memory");
            dto.setMemoriaUsadaMb(round2(usedBytes / MB));
            dto.setMemoriaUsadaPicoMb(round2(parseLong(kv, "used_memory_peak") / MB));
            dto.setMemoriaDisponiveisystemMb(round2(parseLong(kv, "total_system_memory") / MB));
            dto.setMemoriaUsadaDoSistemaPct(ramFisicaBytes > 0
                    ? round2(100.0 * usedBytes / ramFisicaBytes) : 0);

            try {
                dto.setFragmentacaoRatio(
                        Double.parseDouble(kv.getOrDefault("mem_fragmentation_ratio", "0")));
            } catch (NumberFormatException ignored) {}

            dto.setTotalComandosProcessados(parseLong(kv, "total_commands_processed"));
            long hits   = parseLong(kv, "keyspace_hits");
            long misses = parseLong(kv, "keyspace_misses");
            dto.setHitsCache(hits);
            dto.setMissesCache(misses);
            dto.setHitRatioPct((hits + misses) > 0 ? round2(100.0 * hits / (hits + misses)) : 0);

            long totalChaves = 0;
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (e.getKey().startsWith("db")) {
                    String v = e.getValue();
                    int idx = v.indexOf("keys=");
                    if (idx >= 0) {
                        int end = v.indexOf(',', idx);
                        String num = end > 0 ? v.substring(idx + 5, end) : v.substring(idx + 5);
                        try { totalChaves += Long.parseLong(num.trim()); } catch (NumberFormatException ignored) {}
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

    // =========================================================================
    // DB2
    // =========================================================================

    private Db2Dto coletarDb2() {
        Db2Dto dto = new Db2Dto();
        List<TabelaDb2Dto> tabelas = new ArrayList<>();
        String sql = """
            SELECT T.TABNAME, T.TABSCHEMA,
                   COALESCE(T.CARD, -1)        AS CARD,
                   COALESCE(T.FPAGES, 0)       AS FPAGES,
                   COALESCE(TS.PAGESIZE, 8192) AS PAGESIZE,
                   T.STATUS
            FROM SYSCAT.TABLES T
            LEFT JOIN SYSCAT.TABLESPACES TS ON T.TBSPACE = TS.TBSPACE
            WHERE T.TABSCHEMA = 'DB2GFG' AND T.TYPE = 'T'
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
    // PROCESSOS — quem está usando a RAM do servidor
    // =========================================================================

    private List<ProcessoDto> coletarProcessos(long ramFisicaBytes, double ramFisicaGb) {
        List<ProcessoDto> lista = new ArrayList<>();

        // grupos e palavras-chave de busca no caminho do executável
        String[][] alvos = {
            {"CRC/OpenShift VM (qemu-kvm)", "qemu-kvm"},
            {"DB2 (db2sysc)",               "db2sysc"},
            {"JVM ms-operacao",             "java"},
            {"nginx",                       "nginx: worker"},
            {"Tailscale",                   "tailscaled"},
            {"CRC daemon",                  "crc"},
            {"GitHub Runner",               "Runner.Listener"},
        };

        for (String[] alvo : alvos) {
            String label = alvo[0];
            String busca = alvo[1];

            List<ProcessHandle> encontrados = ProcessHandle.allProcesses()
                .filter(p -> {
                    String cmd = p.info().command().orElse("");
                    String args = p.info().commandLine().orElse("");
                    return cmd.contains(busca) || args.contains(busca);
                })
                .limit(4)
                .toList();

            if (encontrados.isEmpty()) continue;

            // agrega RSs de múltiplos workers do mesmo tipo (ex: nginx workers)
            double rssTotal = 0;
            long   pid      = encontrados.get(0).pid();
            for (ProcessHandle ph : encontrados) {
                rssTotal += lerVmRss("/proc/" + ph.pid() + "/status");
            }

            double pct = ramFisicaBytes > 0
                    ? round2(100.0 * rssTotal * MB / ramFisicaBytes) : 0;

            String cmdResumido = encontrados.get(0).info().command()
                    .map(c -> { int i = c.lastIndexOf('/'); return i >= 0 ? c.substring(i+1) : c; })
                    .orElse(busca);
            if (encontrados.size() > 1) cmdResumido += " ×" + encontrados.size();

            lista.add(new ProcessoDto(label, pid, round2(rssTotal), pct, cmdResumido));
        }

        // Ordena por RSS decrescente
        lista.sort((a, b) -> Double.compare(b.getRssMb(), a.getRssMb()));
        return lista;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    private double lerVmRss(String path) {
        try {
            return Files.readAllLines(Path.of(path)).stream()
                    .filter(l -> l.startsWith("VmRSS"))
                    .findFirst()
                    .map(l -> {
                        String[] p = l.trim().split("\\s+");
                        return p.length >= 2 ? Double.parseDouble(p[1]) / 1024.0 : 0.0;
                    })
                    .orElse(0.0);
        } catch (Exception e) { return 0.0; }
    }

    /** Lê um campo do /proc/meminfo em kB. */
    private long lerMeminfokB(String campo) {
        try {
            return Files.readAllLines(Path.of("/proc/meminfo")).stream()
                    .filter(l -> l.startsWith(campo + ":"))
                    .findFirst()
                    .map(l -> Long.parseLong(l.trim().split("\\s+")[1]))
                    .orElse(0L);
        } catch (Exception e) { return 0L; }
    }

    private long ramFisicaTotal() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            return sunOs.getTotalMemorySize();
        }
        return lerMeminfokB("MemTotal") * 1024L;
    }

    private Map<String, String> parseRedisInfo(String info) {
        Map<String, String> map = new HashMap<>();
        for (String linha : info.split("\r?\n")) {
            if (linha.startsWith("#") || linha.isBlank()) continue;
            int sep = linha.indexOf(':');
            if (sep > 0) map.put(linha.substring(0, sep).trim(), linha.substring(sep + 1).trim());
        }
        return map;
    }

    private String formatUptime(long s) {
        return String.format("%dd %02dh %02dm", s / 86400, (s % 86400) / 3600, (s % 3600) / 60);
    }

    private long parseLong(Map<String, String> kv, String key) {
        try { return Long.parseLong(kv.getOrDefault(key, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
