package com.example;

import com.example.domain.RemessaAgente;
import com.example.dto.RemessaDto;
import com.example.repository.RemessaRepository;
import com.example.service.RemessaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários do RemessaService — sem DB2/Redis.
 * Usa stubs manuais para o repositório.
 */
class RemessaServiceTest {

    // Stub manual do RemessaRepository
    private RemessaRepository stubRepository;
    private RemessaService service;

    @BeforeEach
    void setUp() {
        stubRepository = new RemessaRepository() {
            @Override
            public List<RemessaAgente> buscarPorAgente(int codAgente, int limite) {
                return List.of(
                    criarRemessa(1, codAgente, (short) 3),  // Processada
                    criarRemessa(2, codAgente, (short) 1),  // Recebida
                    criarRemessa(3, codAgente, (short) 4)   // Rejeitada
                );
            }

            @Override
            public Optional<RemessaAgente> buscarPorIdEAgente(int idRemessa, int codAgente) {
                if (idRemessa == 1) {
                    return Optional.of(criarRemessa(1, codAgente, (short) 3));
                }
                return Optional.empty();
            }

            @Override
            public List<Object[]> contarPorStatus(int codAgente) {
                return List.of();
            }
        };

        service = new RemessaService();
        // Injeta o stub via reflection para evitar CDI
        try {
            var field = RemessaService.class.getDeclaredField("repository");
            field.setAccessible(true);
            field.set(service, stubRepository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listarPorAgenteDeveRetornarDtosComStatusCorreto() {
        List<RemessaDto> remessas = service.listarPorAgente(5, 10);

        assertEquals(3, remessas.size());
        assertEquals("Processada",  remessas.get(0).getStatus());
        assertEquals("Recebida",    remessas.get(1).getStatus());
        assertEquals("Rejeitada",   remessas.get(2).getStatus());
    }

    @Test
    void listarPorAgenteDevePreencherCamposBasicos() {
        List<RemessaDto> remessas = service.listarPorAgente(5, 10);

        RemessaDto r = remessas.get(0);
        assertEquals(1, r.getId());
        assertEquals(5, r.getCodAgente());
        assertEquals((short) 1, r.getCodFundo());
        assertNotNull(r.getNomeArquivo());
        assertNotNull(r.getRecebidaEm());
    }

    @Test
    void buscarPorIdExistenteDeveRetornarRemessa() {
        Optional<RemessaDto> result = service.buscarPorId(1, 5);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getId());
        assertEquals("Processada", result.get().getStatus());
    }

    @Test
    void buscarPorIdInexistenteDeveRetornarEmpty() {
        Optional<RemessaDto> result = service.buscarPorId(999, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void nomeArquivoDeveTerEspacosTrimados() {
        List<RemessaDto> remessas = service.listarPorAgente(1, 10);
        // nmDtst é CHAR(44) — o serviço deve fazer trim
        assertFalse(remessas.get(0).getNomeArquivo().endsWith(" "));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RemessaAgente criarRemessa(int id, int codAgente, short estRms) {
        return new RemessaAgente(
                id, (short) 1, codAgente, (short) 1,
                String.format("%-44s", "AGT001_202604_001.txt"),
                LocalDate.of(2026, 4, 1),
                LocalDateTime.of(2026, 4, 1, 8, 0),
                estRms == 3 ? LocalDate.of(2026, 4, 2) : null,
                estRms == 3 ? LocalDate.of(2026, 4, 2) : null,
                estRms == 3 ? LocalDate.of(2026, 4, 2) : null,
                estRms == 3 ? LocalDate.of(2026, 4, 3) : null,
                (short) 0,
                new BigDecimal("1500000000.00"),
                null,
                estRms,
                15_000
        );
    }
}
