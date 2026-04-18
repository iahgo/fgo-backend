package com.example.exception;

/**
 * Lançada quando os dados não estão disponíveis no Redis e não é possível
 * carregá-los do DB2 no caminho da requisição.
 *
 * Na arquitetura FGO isso indica que:
 *   - O warm-up ainda não rodou para este mês/agente
 *   - O CacheGuardian ainda não detectou a ausência (janela de até 10 min)
 *
 * Mapeada para HTTP 503 Service Unavailable pelo GlobalExceptionMapper,
 * com header Retry-After: 600 (10 minutos) para orientar o cliente.
 *
 * O Angular deve exibir: "Dados temporariamente indisponíveis. Tente novamente em instantes."
 */
public class DadosNaoDisponiveisException extends RuntimeException {

    private final int codAgente;
    private final String mesAno;

    public DadosNaoDisponiveisException(int codAgente, String mesAno) {
        super(String.format("Dados não disponíveis no Redis para agente=%d mesAno=%s. " +
                "O CacheGuardian irá restaurar em até 10 minutos.", codAgente, mesAno));
        this.codAgente = codAgente;
        this.mesAno    = mesAno;
    }

    public int getCodAgente() { return codAgente; }
    public String getMesAno() { return mesAno; }
}
