package com.example;

import com.example.domain.OperacaoAgregada;
import com.example.dto.OperacaoKpiDto;
import com.example.mapper.OperacaoMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários do OperacaoMapper — sem dependências externas (DB2/Redis).
 */
class OperacaoMapperTest {

    private final OperacaoMapper mapper = new OperacaoMapper();

    // -----------------------------------------------------------------------
    // toKpiDto — campo a campo
    // -----------------------------------------------------------------------

    @Test
    void deveMapearAgregadoParaKpiCorretamente() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "FGO Geral",
                150L,
                new BigDecimal("5000000.00"),
                10L,
                new BigDecimal("250000.00"),
                new BigDecimal("4000000.00")
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertEquals("FGO Geral", dto.getNomePrograma());
        assertEquals(150L, dto.getQuantidadeAtivas());
        assertEquals(new BigDecimal("5000000.00"), dto.getSaldoCarteira());
        assertEquals(10L, dto.getQuantidadeInadimplentes());
        assertEquals(new BigDecimal("250000.00"), dto.getSaldoAtraso());
        assertEquals(new BigDecimal("4000000.00"), dto.getValorGarantia());
    }

    @Test
    void deveCalcularTaxaInadimplenciaCorretamente() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "PRONAMPE",
                200L,
                new BigDecimal("10000000.00"),
                20L,
                new BigDecimal("500000.00"),
                new BigDecimal("8000000.00")
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        // taxaInadimplencia = 20 / 200 = 0.1
        assertEquals(0.1, dto.getTaxaInadimplencia(), 0.000001);
    }

    @Test
    void deveCalcularTaxaInadimplenciaZeroQuandoSemAtivas() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "FGO Rural",
                0L,
                BigDecimal.ZERO,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertEquals(0L, dto.getQuantidadeAtivas());
        assertEquals(BigDecimal.ZERO, dto.getSaldoCarteira());
        assertEquals(0.0, dto.getTaxaInadimplencia(), 0.000001);
    }

    @Test
    void deveMapearProgramaNulo() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                null,
                5L,
                new BigDecimal("100.00"),
                0L,
                BigDecimal.ZERO,
                new BigDecimal("80.00")
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertNull(dto.getNomePrograma());
        assertEquals(5L, dto.getQuantidadeAtivas());
        assertEquals(new BigDecimal("80.00"), dto.getValorGarantia());
    }

    @Test
    void deveCalcularTaxaInadimplenciaArredondada() {
        // 1 / 3 = 0.333333...
        OperacaoAgregada agregado = new OperacaoAgregada(
                "FGI",
                3L,
                new BigDecimal("300.00"),
                1L,
                new BigDecimal("50.00"),
                new BigDecimal("240.00")
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        // Deve arredondar para 6 casas decimais
        assertEquals(0.333333, dto.getTaxaInadimplencia(), 0.000001);
    }

    @Test
    void deveMapearTaxaInadQuandoTodosInadimplentes() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "Emergencial Investimento",
                50L,
                new BigDecimal("2500000.00"),
                50L,
                new BigDecimal("2500000.00"),
                new BigDecimal("2000000.00")
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertEquals(1.0, dto.getTaxaInadimplencia(), 0.000001);
        assertEquals(50L, dto.getQuantidadeInadimplentes());
    }

    // -----------------------------------------------------------------------
    // toResumoDto — estrutura do DTO completo
    // -----------------------------------------------------------------------

    @Test
    void toResumoDtoDevePreencherMetadados() {
        var dados = java.util.List.of(
            new OperacaoAgregada("PRONAMPE", 100L, new BigDecimal("1000000.00"), 5L,
                new BigDecimal("50000.00"), new BigDecimal("800000.00")),
            new OperacaoAgregada("FGI", 200L, new BigDecimal("2000000.00"), 10L,
                new BigDecimal("100000.00"), new BigDecimal("1600000.00"))
        );

        var resumo = mapper.toResumoDto(8, dados);

        assertEquals(8, resumo.getCodigoAgente());
        assertEquals(2, resumo.getProgramas().size());
        assertNotNull(resumo.getCarregadoEm());
    }

    @Test
    void toResumoDtoComListaVaziaDeveRetornarSemProgramas() {
        var resumo = mapper.toResumoDto(3, java.util.List.<OperacaoAgregada>of());

        assertEquals(3, resumo.getCodigoAgente());
        assertTrue(resumo.getProgramas().isEmpty());
    }
}
