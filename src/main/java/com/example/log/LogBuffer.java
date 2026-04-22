package com.example.log;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer circular em memória que armazena as últimas N linhas de log.
 * Thread-safe via synchronized — baixo contention esperado.
 *
 * Alimentado pelo LogBufferHandler registrado no startup.
 * Exposto via GET /admin/logs.
 */
@ApplicationScoped
public class LogBuffer {

    private static final int MAX = 2_000;

    private final Deque<LogEntry> buffer = new ArrayDeque<>(MAX);

    public synchronized void add(LogEntry entry) {
        if (buffer.size() >= MAX) {
            buffer.pollFirst();
        }
        buffer.addLast(entry);
    }

    /**
     * Retorna as últimas {@code limit} entradas de log, da mais antiga para a mais recente.
     */
    public synchronized List<LogEntry> getLast(int limit) {
        int skip = Math.max(0, buffer.size() - limit);
        return buffer.stream().skip(skip).toList();
    }

    public synchronized int size() {
        return buffer.size();
    }

    // -------------------------------------------------------------------------

    public record LogEntry(
            LocalDateTime timestamp,
            String nivel,
            String logger,
            String mensagem
    ) {}
}
