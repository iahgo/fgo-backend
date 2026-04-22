package com.example.log;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Registra um java.util.logging.Handler no root logger do JVM no startup.
 * O JBoss LogManager (usado pelo Quarkus) delega para ele todos os eventos de log.
 *
 * Captura as últimas 2.000 linhas de log para o /admin/logs endpoint.
 */
@ApplicationScoped
public class LogBufferHandler {

    private static final Logger LOG = Logger.getLogger(LogBufferHandler.class);

    @Inject
    LogBuffer buffer;

    void onStart(@Observes StartupEvent event) {
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                String nivel = record.getLevel().getName();
                String logger = record.getLoggerName();
                if (logger == null) logger = "root";
                // Limita nome do logger para não poluir
                if (logger.length() > 50) logger = "..." + logger.substring(logger.length() - 47);

                String msg = record.getMessage();
                // Formata parâmetros se houver
                if (record.getParameters() != null && record.getParameters().length > 0) {
                    try {
                        msg = String.format(msg.replace("{}", "%s"), record.getParameters());
                    } catch (Exception ignored) {}
                }

                LocalDateTime ts = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(record.getMillis()),
                        ZoneId.systemDefault()
                );
                buffer.add(new LogBuffer.LogEntry(ts, nivel, logger, msg));
            }

            @Override public void flush() {}
            @Override public void close() {}
        });
        LOG.info("[LOG-BUFFER] Handler registrado — capturando logs em memória (últimas 2.000 linhas).");
    }
}
