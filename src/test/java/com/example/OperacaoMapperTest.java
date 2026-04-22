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

    @Test
    void deveMapearAgregadoParaKpiCorretamente() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "FGO Geral",
                150L,
                new BigDecimal("5000000.00"),
                10L
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertEquals("FGO Geral", dto.getPrograma());
        assertEquals(150L, dto.getTotalAtivas());
        assertEquals(new BigDecimal("5000000.00"), dto.getVlrCarteira());
        assertEquals(10L, dto.getTotalInad());
    }

    @Test
    void deveCalcularTaxaInadimplenciaZeroQuandoSemAtivas() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                "FGO Rural",
                0L,
                BigDecimal.ZERO,
                0L
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertEquals(0L, dto.getTotalAtivas());
        assertEquals(BigDecimal.ZERO, dto.getVlrCarteira());
    }

    @Test
    void deveMapearProgramaNulo() {
        OperacaoAgregada agregado = new OperacaoAgregada(
                null,
                5L,
                new BigDecimal("100.00"),
                0L
        );

        OperacaoKpiDto dto = mapper.toKpiDto(agregado);

        assertNull(dto.getPrograma());
        assertEquals(5L, dto.getTotalAtivas());
    }
}
