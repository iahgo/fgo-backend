package com.example.listener;

import com.example.loader.OperacaoLoader;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 * Subscriber Redis Pub/Sub para reload manual do domínio operação.
 *
 * =========================================================================
 * FLUXO DE RELOAD MANUAL (cf. arquitetura FGO, seção 13)
 * =========================================================================
 *
 * Quando o ETL é reprocessado fora do horário normal (ex: às 14h com dados corrigidos),
 * o gestor do FGO pode forçar a recarga via Angular (tela de gestão interna):
 *
 *   Gestor clica "Recarregar Operação"
 *     → MS Administração FGO (protegido por ROLE_GESTOR_FGO)
 *     → PUBLISH fgo:admin:reload "operacao"    ← publica no canal Redis
 *     → este subscriber recebe a mensagem
 *     → verifica se é seu domínio ("operacao" ou "todos")
 *     → dispara recarregarTudo() em background
 *
 * Vantagem sobre deletar + aguardar Guardian:
 *   - Reload direto: sobrescreve a chave → zero downtime para o usuário
 *   - Delete + Guardian: até 10 minutos de dado vazio
 *
 * =========================================================================
 * MENSAGENS SUPORTADAS
 * =========================================================================
 *
 *   "operacao" → recarrega apenas o domínio operação (todos os agentes)
 *   "todos"    → recarrega todos os domínios — cada MS reage ao receber "todos"
 *   qualquer outro valor → ignorado (mensagem de outro domínio)
 *
 * =========================================================================
 * CANAL
 * =========================================================================
 *
 *   fgo:admin:reload
 *
 * O subscription é registrado no StartupEvent para garantir que o MS começa
 * a ouvir imediatamente ao subir, antes de aceitar qualquer tráfego.
 */
@ApplicationScoped
public class OperacaoReloadSubscriber {

    private static final Logger LOG = Logger.getLogger(OperacaoReloadSubscriber.class);

    private static final String CANAL          = "fgo:admin:reload";
    private static final String DOMINIO_PROPRIO = "operacao";
    private static final String DOMINIO_TODOS   = "todos";

    @Inject
    RedisDataSource redis;

    @Inject
    OperacaoLoader loader;

    @Inject
    ManagedExecutor executor;

    void onStart(@Observes StartupEvent event) {
        redis.pubsub(String.class).subscribe(CANAL, this::onMensagem);
        LOG.infof("[PUBSUB] Inscrito no canal '%s' — aguardando mensagens de reload.", CANAL);
    }

    private void onMensagem(String mensagem) {
        if (mensagem == null) return;

        String dominio = mensagem.trim().toLowerCase();

        if (!DOMINIO_PROPRIO.equals(dominio) && !DOMINIO_TODOS.equals(dominio)) {
            LOG.debugf("[PUBSUB] Mensagem '%s' ignorada — domínio diferente de '%s'.",
                    mensagem, DOMINIO_PROPRIO);
            return;
        }

        LOG.infof("[PUBSUB] Mensagem '%s' recebida no canal '%s' — disparando recarga de todos os agentes.",
                mensagem, CANAL);

        executor.submit(loader::recarregarTudo);
    }
}
