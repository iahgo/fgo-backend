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

    @Inject OperacaoRepository db2Repository;
    @Inject OperacaoRedisRepository redisRepository;
    @Inject OperacaoMapper mapper;
    @Inject ManagedExecutor executor;

    @Scheduled(cron = "0 20 7 * * ?")
    public void warmUpDiario() {
        LOG.info("=== [WARM-UP] Iniciando carga diaria DB2 -> Redis ===");
        carregarTodosComRetry("warm-up-diario");
    }

    void onStart(@Observes StartupEvent event) {
        LOG.info("=== [STARTUP] Disparando warm-up em background ===");
        executor.submit(() -> {
            try {
                carregarTodosComRetry("startup-bg");
            } catch (Exception e) {
                LOG.errorf("[STARTUP] Erro no warm-up: %s", e.getMessage());
            }
        });
    }

    @Scheduled(every = "10m")
    public void cacheGuardian() {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) return;
        int agentePiloto = agentes.get(0);
        if (redisRepository.existe(agentePiloto)) return;
        LOG.warnf("[GUARDIAN] Cache ausente para agente=%d — disparando warm-up.", agentePiloto);
        carregarTodosComRetry("guardian");
    }

    public void recarregarTudo() {
        LOG.info("[RELOAD] Recarga manual — todos os agentes.");
        carregarTodosComRetry("reload-manual");
    }

    public void recarregarAgente(int codAgente) {
        LOG.infof("[RELOAD] Recarga manual — agente=%d", codAgente);
        try {
            carregarAgente(codAgente);
        } catch (Exception e) {
            LOG.errorf("[RELOAD] Falha ao recarregar agente=%d: %s", codAgente, e.getMessage());
            throw e;
        }
    }

    private void carregarTodosComRetry(String origem) {
        List<Integer> agentes = agentesAtivos();
        if (agentes.isEmpty()) {
            LOG.warnf("[%s] Nenhum agente ativo — nada a carregar.", origem);
            return;
        }

        long inicio = System.currentTimeMillis();
        List<Integer> falhas = new ArrayList<>();

        for (int codAgente : agentes) {
            try {
                carregarAgente(codAgente);
            } catch (Exception e) {
                LOG.errorf("[%s] FALHA P1 | agente=%d | %s", origem, codAgente, e.getMessage());
                falhas.add(codAgente);
            }
        }

        List<Integer> falhasFinais = new ArrayList<>();
        for (int codAgente : falhas) {
            try {
                carregarAgente(codAgente);
                LOG.infof("[%s] RECUPERADO na P2 | agente=%d", origem, codAgente);
            } catch (Exception e) {
                LOG.errorf("[%s] FALHA P2 | agente=%d | %s", origem, codAgente, e.getMessage());
                falhasFinais.add(codAgente);
            }
        }

        long ms = System.currentTimeMillis() - inicio;
        LOG.infof("[%s] Concluido em %dms | %d/%d agentes", origem, ms,
                agentes.size() - falhasFinais.size(), agentes.size());
    }

    private void carregarAgente(int codAgente) {
        if (!redisRepository.adquirirLock(codAgente)) {
            LOG.debugf("[LOADER] agente=%d lock ocupado, pulando.", codAgente);
            return;
        }
        try {
            List<OperacaoAgregada> dados = db2Repository.buscarAgregadoPorAgente(codAgente);
            OperacaoResumoDto dto = mapper.toResumoDto(codAgente, dados);
            redisRepository.salvar(dto);
            LOG.debugf("[LOADER] agente=%d | %d programa(s) -> Redis", codAgente, dados.size());
        } finally {
            redisRepository.liberarLock(codAgente);
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
