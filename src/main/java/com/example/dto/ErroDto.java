package com.example.dto;

import java.time.LocalDateTime;

/**
 * DTO padrão de resposta de erro.
 *
 * Retornado pelo GlobalExceptionMapper em toda exceção não tratada
 * e por validações de negócio que resultam em erro HTTP.
 *
 * Exemplo de resposta:
 * {
 *   "codigo": "AGENTE_NAO_HABILITADO",
 *   "mensagem": "Agente 999 não está habilitado no FGO.",
 *   "timestamp": "2025-04-17T10:30:00"
 * }
 */
public class ErroDto {

    /** Código de erro de negócio (sem espaços, maiúsculo). */
    private String codigo;

    /** Mensagem legível para logs e diagnóstico. */
    private String mensagem;

    /** Momento do erro. */
    private LocalDateTime timestamp;

    public ErroDto() {}

    public ErroDto(String codigo, String mensagem) {
        this.codigo    = codigo;
        this.mensagem  = mensagem;
        this.timestamp = LocalDateTime.now();
    }

    public String getCodigo()              { return codigo; }
    public void setCodigo(String v)        { this.codigo = v; }

    public String getMensagem()            { return mensagem; }
    public void setMensagem(String v)      { this.mensagem = v; }

    public LocalDateTime getTimestamp()    { return timestamp; }
    public void setTimestamp(LocalDateTime v) { this.timestamp = v; }
}
