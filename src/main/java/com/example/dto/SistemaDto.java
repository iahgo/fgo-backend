package com.example.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de resposta do endpoint GET /admin/sistema.
 * Consolidado completo de métricas do servidor, JVM, Redis e DB2.
 */
public class SistemaDto {

    private ServidorDto servidor;
    private JvmDto jvm;
    private RedisDto redis;
    private Db2Dto db2;
    private LocalDateTime geradoEm;

    public SistemaDto() {}

    public SistemaDto(ServidorDto servidor, JvmDto jvm, RedisDto redis, Db2Dto db2, LocalDateTime geradoEm) {
        this.servidor  = servidor;
        this.jvm       = jvm;
        this.redis     = redis;
        this.db2       = db2;
        this.geradoEm  = geradoEm;
    }

    public ServidorDto getServidor()          { return servidor; }
    public void setServidor(ServidorDto v)    { this.servidor = v; }
    public JvmDto getJvm()                   { return jvm; }
    public void setJvm(JvmDto v)             { this.jvm = v; }
    public RedisDto getRedis()               { return redis; }
    public void setRedis(RedisDto v)         { this.redis = v; }
    public Db2Dto getDb2()                   { return db2; }
    public void setDb2(Db2Dto v)             { this.db2 = v; }
    public LocalDateTime getGeradoEm()       { return geradoEm; }
    public void setGeradoEm(LocalDateTime v) { this.geradoEm = v; }

    // -------------------------------------------------------------------------

    public static class ServidorDto {
        private String hostname;
        private String os;
        private String arquitetura;
        private int cpus;
        private double loadAvg1m;
        private double memoriaFisicaTotalGb;
        private double memoriaFisicaLivreGb;
        private double memoriaFisicaUsadaGb;
        private double memoriaFisicaUsadaPct;
        private long uptimeMs;
        private List<DiscoDto> discos;

        public String getHostname()                         { return hostname; }
        public void setHostname(String v)                   { this.hostname = v; }
        public String getOs()                               { return os; }
        public void setOs(String v)                         { this.os = v; }
        public String getArquitetura()                      { return arquitetura; }
        public void setArquitetura(String v)                { this.arquitetura = v; }
        public int getCpus()                                { return cpus; }
        public void setCpus(int v)                          { this.cpus = v; }
        public double getLoadAvg1m()                        { return loadAvg1m; }
        public void setLoadAvg1m(double v)                  { this.loadAvg1m = v; }
        public double getMemoriaFisicaTotalGb()             { return memoriaFisicaTotalGb; }
        public void setMemoriaFisicaTotalGb(double v)       { this.memoriaFisicaTotalGb = v; }
        public double getMemoriaFisicaLivreGb()             { return memoriaFisicaLivreGb; }
        public void setMemoriaFisicaLivreGb(double v)       { this.memoriaFisicaLivreGb = v; }
        public double getMemoriaFisicaUsadaGb()             { return memoriaFisicaUsadaGb; }
        public void setMemoriaFisicaUsadaGb(double v)       { this.memoriaFisicaUsadaGb = v; }
        public double getMemoriaFisicaUsadaPct()            { return memoriaFisicaUsadaPct; }
        public void setMemoriaFisicaUsadaPct(double v)      { this.memoriaFisicaUsadaPct = v; }
        public long getUptimeMs()                           { return uptimeMs; }
        public void setUptimeMs(long v)                     { this.uptimeMs = v; }
        public List<DiscoDto> getDiscos()                   { return discos; }
        public void setDiscos(List<DiscoDto> v)             { this.discos = v; }
    }

    public static class DiscoDto {
        private String caminho;
        private double totalGb;
        private double livreGb;
        private double usadoGb;
        private double usadoPct;

        public DiscoDto(String caminho, double totalGb, double livreGb, double usadoGb, double usadoPct) {
            this.caminho  = caminho;
            this.totalGb  = totalGb;
            this.livreGb  = livreGb;
            this.usadoGb  = usadoGb;
            this.usadoPct = usadoPct;
        }

        public String getCaminho()      { return caminho; }
        public double getTotalGb()      { return totalGb; }
        public double getLivreGb()      { return livreGb; }
        public double getUsadoGb()      { return usadoGb; }
        public double getUsadoPct()     { return usadoPct; }
    }

    public static class JvmDto {
        private String versao;
        private double heapUsadoMb;
        private double heapMaxMb;
        private double heapUsadoPct;
        private double nonHeapUsadoMb;
        private int threadsAtivos;
        private int threadsTotal;
        private long uptimeMs;

        public String getVersao()            { return versao; }
        public void setVersao(String v)      { this.versao = v; }
        public double getHeapUsadoMb()       { return heapUsadoMb; }
        public void setHeapUsadoMb(double v) { this.heapUsadoMb = v; }
        public double getHeapMaxMb()         { return heapMaxMb; }
        public void setHeapMaxMb(double v)   { this.heapMaxMb = v; }
        public double getHeapUsadoPct()      { return heapUsadoPct; }
        public void setHeapUsadoPct(double v){ this.heapUsadoPct = v; }
        public double getNonHeapUsadoMb()    { return nonHeapUsadoMb; }
        public void setNonHeapUsadoMb(double v){ this.nonHeapUsadoMb = v; }
        public int getThreadsAtivos()        { return threadsAtivos; }
        public void setThreadsAtivos(int v)  { this.threadsAtivos = v; }
        public int getThreadsTotal()         { return threadsTotal; }
        public void setThreadsTotal(int v)   { this.threadsTotal = v; }
        public long getUptimeMs()            { return uptimeMs; }
        public void setUptimeMs(long v)      { this.uptimeMs = v; }
    }

    public static class RedisDto {
        private String versao;
        private String modo;           // standalone / sentinel / cluster
        private double memoriaUsadaMb;
        private double memoriaUsadaPicoMb;
        private double memoriaDisponiveisystemMb;
        private long totalChaves;
        private long clientesConectados;
        private long totalComandosProcessados;
        private long hitsCache;
        private long missesCache;
        private double hitRatioPct;
        private String uptimeServidor;
        private String erro;

        public String getVersao()                          { return versao; }
        public void setVersao(String v)                    { this.versao = v; }
        public String getModo()                            { return modo; }
        public void setModo(String v)                      { this.modo = v; }
        public double getMemoriaUsadaMb()                  { return memoriaUsadaMb; }
        public void setMemoriaUsadaMb(double v)            { this.memoriaUsadaMb = v; }
        public double getMemoriaUsadaPicoMb()              { return memoriaUsadaPicoMb; }
        public void setMemoriaUsadaPicoMb(double v)        { this.memoriaUsadaPicoMb = v; }
        public double getMemoriaDisponiveisystemMb()       { return memoriaDisponiveisystemMb; }
        public void setMemoriaDisponiveisystemMb(double v) { this.memoriaDisponiveisystemMb = v; }
        public long getTotalChaves()                        { return totalChaves; }
        public void setTotalChaves(long v)                  { this.totalChaves = v; }
        public long getClientesConectados()                 { return clientesConectados; }
        public void setClientesConectados(long v)           { this.clientesConectados = v; }
        public long getTotalComandosProcessados()           { return totalComandosProcessados; }
        public void setTotalComandosProcessados(long v)     { this.totalComandosProcessados = v; }
        public long getHitsCache()                          { return hitsCache; }
        public void setHitsCache(long v)                    { this.hitsCache = v; }
        public long getMissesCache()                        { return missesCache; }
        public void setMissesCache(long v)                  { this.missesCache = v; }
        public double getHitRatioPct()                      { return hitRatioPct; }
        public void setHitRatioPct(double v)                { this.hitRatioPct = v; }
        public String getUptimeServidor()                   { return uptimeServidor; }
        public void setUptimeServidor(String v)             { this.uptimeServidor = v; }
        public String getErro()                             { return erro; }
        public void setErro(String v)                       { this.erro = v; }
    }

    public static class Db2Dto {
        private List<TabelaDb2Dto> tabelas;
        private String erro;

        public List<TabelaDb2Dto> getTabelas() { return tabelas; }
        public void setTabelas(List<TabelaDb2Dto> v) { this.tabelas = v; }
        public String getErro()               { return erro; }
        public void setErro(String v)         { this.erro = v; }
    }

    public static class TabelaDb2Dto {
        private String nome;
        private String schema;
        private long estimativaLinhas;
        private long paginasArquivo;
        private int tamanhoPaginaBytes;
        private double tamanhoMb;
        private double tamanhoGb;
        private String status;

        public TabelaDb2Dto(String nome, String schema, long estimativaLinhas,
                            long paginasArquivo, int tamanhoPaginaBytes, String status) {
            this.nome               = nome;
            this.schema             = schema;
            this.estimativaLinhas   = estimativaLinhas;
            this.paginasArquivo     = paginasArquivo;
            this.tamanhoPaginaBytes = tamanhoPaginaBytes;
            this.status             = status;
            long bytes              = paginasArquivo * (long) tamanhoPaginaBytes;
            this.tamanhoMb          = round2(bytes / 1024.0 / 1024.0);
            this.tamanhoGb          = round2(bytes / 1024.0 / 1024.0 / 1024.0);
        }

        private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

        public String getNome()               { return nome; }
        public String getSchema()             { return schema; }
        public long getEstimativaLinhas()     { return estimativaLinhas; }
        public long getPaginasArquivo()       { return paginasArquivo; }
        public int getTamanhoPaginaBytes()    { return tamanhoPaginaBytes; }
        public double getTamanhoMb()          { return tamanhoMb; }
        public double getTamanhoGb()          { return tamanhoGb; }
        public String getStatus()             { return status; }
    }
}
