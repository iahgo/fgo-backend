package com.example.dto.painel;

/**
 * DTO de resumo de remessas do painel.
 */
public record RemessasResumoDto(long esperadas, long naoMovimentadas, long concluidas) {
}
