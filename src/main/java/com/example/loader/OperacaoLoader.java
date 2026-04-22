package com.example.loader;

import com.example.domain.OperacaoAgregada;
import com.example.dto.OperacaoResumoDto;
import com.example.mapper.OperacaoMapper;
import com.example.repository.OperacaoRedisRepository;
import com.example.repository.OperacaoRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Componente de infraestrutura responsável pelo carregamento proativo de dados
 * do DB2 no Redis (warm-up).
 *
 * A lista de agentes vem da tabela AGENTE no DB2 — não de configuração estática.
 * Quando a model Agente estiver criada, buscarAgentesAtivos() será substituído
 * por AgenteRepository.buscarTodosHabilitados().
 *
 * =========================================================================
 * TRÊS MECANISMOS DE CARGA
 * =========================================================================
 *
 * 1. WARM-UP DIÁRIO (@Scheduled às 07:20)
 * 2. STARTUP EVENT (ao subir o pod)
 * 3. CACHE GUARDIAN (@Scheduled a cada 10 minutos)
 */
@ApplicationScoped
public class OperacaoLoader {

    private static final Logger LOG = Logger.getLogger(OperacaoLoader.class);
    private static final DateTimeFormatter FMT_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    @Inject OperacaoRepository db2Repository;
    @Inject OperacaoRedisRepository redisRepository;
    @Inject OperacaoMapper mapper;

    // =========================================================================
    // MECANISMO 1: WARM-UP DIÁRIO
    // =========================================================================

    @Scheduled(cron = "0 20 7 * * ?")
    public void warmUpDiario() {
        LOG.info("=== [WARM-UP] Iniciando carga diária DB2 → Redis ===");
        carregarTodosComRetry("warm-up-diario");
    }

    // =========================================================================
    // MECANISMO 2: STARTUP EVENT
    // =========================================================================

    void onStart(@Observes StartupEvent event) {
        LOG.info("=== [STARTUP] Verificando Redis antes de aceitar tráfego ===");

        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) {
            LOG.warn("[STARTUP] Nenhum agente ativo encontrado no DB2. Pod sobe sem dados no Redis.");
            return;
        }

        String mesAno = mesAtual();
        List<Integer> semDados = new ArrayList<>();

        for (int codAgente : agentes) {
            if (!redisRepository.existe(codAgente, mesAno)) {
                semDados.add(codAgente);
            }
        }

        if (semDados.isEmpty()) {
            LOG.infof("[STARTUP] Redis OK. %d agentes com dados. Pod pronto.", agentes.size());
            return;
        }

        LOG.warnf("[STARTUP] %d agente(s) sem dados. Carregando do DB2...", semDados.size());
        for (int codAgente : semDados) {
            try {
                carregarAgente(codAgente, mesAno);
            } catch (Exception e) {
                LOG.errorf("[STARTUP] Falha no agente %d: %s — CacheGuardian corrige em 10min.", codAgente, e.getMessage());
            }
        }
        LOG.info("[STARTUP] Concluído. Pod pronto para tráfego.");
    }

    // =========================================================================
    // MECANISMO 3: CACHE GUARDIAN
    // =========================================================================

    @Scheduled(every = "10m")
    public void cacheGuardian() {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) {
            return;
        }

        // Usa o primeiro agente ativo como âncora — vem do DB2, sem configuração
        int agentePiloto = agentes.get(0);
        String mesAno = mesAtual();

        if (redisRepository.existe(agentePiloto, mesAno)) {
            return;
        }

        LOG.warnf("[GUARDIAN] Chave âncora ausente (agente=%d)! Redis pode ter perdido dados. Disparando warm-up.", agentePiloto);
        carregarTodosComRetry("guardian");
    }

    // =========================================================================
    // RELOAD MANUAL
    // =========================================================================

    /**
     * Recarrega todos os agentes para o mês atual.
     * Chamado pelo endpoint /admin/reload ou pelo Pub/Sub subscriber
     * quando o MS Admin publica "operacao" ou "todos" no canal fgo:admin:reload.
     */
    public void recarregarTudo() {
        LOG.info("[RELOAD] Recarga manual solicitada — todos os agentes.");
        carregarTodosComRetry("reload-manual");
    }

    /**
     * Recarrega um único agente para o mês atual.
     * Chamado pelo endpoint /admin/reload/{codAgente} quando o gestor precisa
     * atualizar apenas um agente específico (ex: reprocessamento pontual às 14h).
     *
     * Usa o mesmo lock distribuído SET NX do warm-up normal para evitar
     * concorrência entre pods em ambientes com múltiplas réplicas.
     *
     * @param codAgente código interno do agente a recarregar
     */
    public void recarregarAgente(int codAgente) {
        LOG.infof("[RELOAD] Recarga manual solicitada — agente=%d", codAgente);
        String mesAno = mesAtual();
        try {
            carregarAgente(codAgente, mesAno);
            LOG.infof("[RELOAD] Agente=%d recarregado com sucesso | mes=%s", codAgente, mesAno);
        } catch (Exception e) {
            LOG.errorf("[RELOAD] Falha ao recarregar agente=%d: %s", codAgente, e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // LÓGICA CENTRAL: 2 PASSAGENS COM RETRY
    // =========================================================================

    private void carregarTodosComRetry(String origem) {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) {
            LOG.warnf("[%s] Nenhum agente ativo no DB2 — nada a carregar.", origem);
            return;
        }

        String mesAno = mesAtual();
        long inicio = System.currentTimeMillis();
        List<Integer> falhas = new ArrayList<>();

        // Passagem 1
        for (int codAgente : agentes) {
            try {
                carregarAgente(codAgente, mesAno);
            } catch (Exception e) {
                LOG.errorf("[%s] FALHA P1 | agente=%d | %s", origem, codAgente, e.getMessage());
                falhas.add(codAgente);
            }
        }

        // Passagem 2: retry dos que falharam
        List<Integer> falhasFinais = new ArrayList<>();
        for (int codAgente : falhas) {
            try {
                carregarAgente(codAgente, mesAno);
                LOG.infof("[%s] RECUPERADO na P2 | agente=%d", origem, codAgente);
            } catch (Exception e) {
                LOG.errorf("[%s] FALHA P2 (definitiva) | agente=%d | %s", origem, codAgente, e.getMessage());
                falhasFinais.add(codAgente);
            }
        }

        long ms = System.currentTimeMillis() - inicio;
        int ok = agentes.size() - falhasFinais.size();
        LOG.infof("[%s] Concluído em %dms | %d/%d agentes | mes=%s",
                origem, ms, ok, agentes.size(), mesAno);

        if (!falhasFinais.isEmpty()) {
            LOG.errorf("[%s] Agentes sem dados: %s", origem, falhasFinais);
        }
    }

    // =========================================================================
    // CARGA DE UM AGENTE (com lock distribuído SET NX)
    // =========================================================================

    private void carregarAgente(int codAgente, String mesAno) {
        if (!redisRepository.adquirirLock(codAgente)) {
            LOG.debugf("[LOADER] agente=%d lock ocupado por outro pod, pulando.", codAgente);
            return;
        }

        try {
            String[] partes = mesAno.split("-");
            int ano = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);

            List<OperacaoAgregada> dados = db2Repository.buscarAgregadoPorAgenteMes(codAgente, ano, mes);
            OperacaoResumoDto dto = mapper.toResumoDto(codAgente, mesAno, dados);
            redisRepository.salvar(dto);

            LOG.debugf("[LOADER] agente=%d | %d programa(s) → Redis", codAgente, dados.size());
        } finally {
            redisRepository.liberarLock(codAgente);
        }
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    /**
     * Busca agentes ativos do DB2.
     * TODO: substituir por AgenteRepository.buscarTodosHabilitados()
     *       quando a model Agente estiver criada.
     */
    private List<Integer> agentesAtivos() {
        try {
            return db2Repository.buscarAgentesAtivos();
        } catch (Exception e) {
            LOG.errorf("[LOADER] Falha ao buscar agentes no DB2: %s", e.getMessage());
            return List.of();
        }
    }

    private String mesAtual() {
        return YearMonth.now().format(FMT_MES);
    }
}
