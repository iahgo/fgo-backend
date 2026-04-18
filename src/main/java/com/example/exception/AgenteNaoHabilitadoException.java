package com.example.exception;

/**
 * Lançada quando o cod_agente do header X-Cod-Agente não está na lista
 * de agentes habilitados configurada em fgo.agentes.codigos.
 *
 * Mapeada para HTTP 403 Forbidden pelo GlobalExceptionMapper.
 * (Não é 404 — o agente existe, mas não tem acesso ao FGO.)
 */
public class AgenteNaoHabilitadoException extends RuntimeException {

    private final int codAgente;

    public AgenteNaoHabilitadoException(int codAgente) {
        super("Agente " + codAgente + " não está habilitado no FGO.");
        this.codAgente = codAgente;
    }

    public int getCodAgente() {
        return codAgente;
    }
}
