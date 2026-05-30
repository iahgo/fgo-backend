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
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OperacaoLoader {

    private static final Logger LOG = Logger.getLogger(OperacaoLoader.class);
    private static final String POD = System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local";

    @Inject OperacaoRepository db2Repository;
    @Inject OperacaoRedisRepository redisRepository;
    @Inject OperacaoMapper mapper;
    @Inject ManagedExecutor executor;

    @Scheduled(cron = "0 20 7 * * ?")
    public void warmUpDiario() {
        LOG.infof("=== [WARM-UP] pod=%s | Iniciando carga diaria DB2 -> Redis ===", POD);
        carregarTodosComRetry("warm-up-diario");
    }

    void onStart(@Observes StartupEvent event) {
        LOG.infof("=== [STARTUP] pod=%s | Disparando warm-up em background ===", POD);
        executor.submit(() -> {
            try {
                carregarTodosComRetry("startup-bg");
            } catch (Exception e) {
                LOG.errorf("[STARTUP] pod=%s | Erro no warm-up: %s", POD, e.getMessage());
            }
        });
    }

    @Scheduled(every = "10m")
    public void cacheGuardian() {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) return;
        int agentePiloto = agentes.get(0);
        if (redisRepository.existe(agentePiloto)) return;
        LOG.warnf("[GUARDIAN] pod=%s | Cache ausente para agente=%d — disparando warm-up.", POD, agentePiloto);
        carregarTodosComRetry("guardian");
    }

    public void recarregarTudo() {
        LOG.infof("[RELOAD] pod=%s | Recarga manual — todos os agentes.", POD);
        carregarTodosComRetry("reload-manual");
    }

    public void recarregarAgente(int codAgente) {
        LOG.infof("[RELOAD] pod=%s | Recarga manual — agente=%d", POD, codAgente);
        try {
            carregarAgente(codAgente);
        } catch (Exception e) {
            LOG.errorf("[RELOAD] pod=%s | Falha ao recarregar agente=%d: %s", POD, codAgente, e.getMessage());
            throw e;
        }
    }

    private void carregarTodosComRetry(String origem) {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) {
            LOG.warnf("[%s] pod=%s | Nenhum agente ativo — nada a carregar.", origem, POD);
            return;
        }

        long inicio = System.currentTimeMillis();
        List<Integer> falhas = new ArrayList<>();

        for (int codAgente : agentes) {
            try {
                carregarAgente(codAgente);
            } catch (Exception e) {
                LOG.errorf("[%s] pod=%s | FALHA P1 | agente=%d | %s", origem, POD, codAgente, e.getMessage());
                falhas.add(codAgente);
            }
        }

        List<Integer> falhasFinais = new ArrayList<>();
        for (int codAgente : falhas) {
            try {
                carregarAgente(codAgente);
                LOG.infof("[%s] pod=%s | RECUPERADO na P2 | agente=%d", origem, POD, codAgente);
            } catch (Exception e) {
                LOG.errorf("[%s] pod=%s | FALHA P2 | agente=%d | %s", origem, POD, codAgente, e.getMessage());
                falhasFinais.add(codAgente);
            }
        }

        long ms = System.currentTimeMillis() - inicio;
        LOG.infof("[%s] pod=%s | Concluido em %dms | %d/%d agentes", origem, POD, ms,
                agentes.size() - falhasFinais.size(), agentes.size());
    }

    private void carregarAgente(int codAgente) {
        if (!redisRepository.adquirirLock(codAgente)) {
            LOG.infof("[LOADER] pod=%s | agente=%d | LOCK OCUPADO — outro pod esta carregando, pulando.", POD, codAgente);
            return;
        }
        try {
            LOG.infof("[LOADER] pod=%s | agente=%d | LOCK ADQUIRIDO — iniciando carga do DB2...", POD, codAgente);
            long inicio = System.currentTimeMillis();
            List<OperacaoAgregada> dados = db2Repository.buscarAgregadoPorAgente(codAgente);
            OperacaoResumoDto dto = mapper.toResumoDto(codAgente, dados);
            redisRepository.salvar(dto);
            LOG.infof("[LOADER] pod=%s | agente=%d | %d programa(s) gravados no Redis em %dms",
                    POD, codAgente, dados.size(), System.currentTimeMillis() - inicio);
        } finally {
            redisRepository.liberarLock(codAgente);
            LOG.infof("[LOADER] pod=%s | agente=%d | lock liberado.", POD, codAgente);
        }
    }

    private List<Integer> agentesAtivos() {
        try {
            return db2Repository.buscarAgentesAtivos();
        } catch (Exception e) {
            LOG.errorf("[LOADER] Falha ao buscar agentes: %s", e.getMessage());
            return List.of();
        }
    }
}
