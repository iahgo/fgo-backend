package com.example.exception;

import com.example.dto.ErroDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.stream.Collectors;

/**
 * Intercepta todas as exceções não tratadas pelos resources e retorna
 * um ErroDto padronizado em vez de stack trace exposto ao cliente.
 *
 * Ordem de tratamento:
 *   1. Exceções de negócio FGO (AgenteNaoHabilitado, DadosNaoDisponiveis)
 *   2. Exceções de validação (Bean Validation — parâmetros inválidos)
 *   3. Exceções HTTP passadas explicitamente (WebApplicationException)
 *   4. Qualquer outra exceção → 500 Internal Server Error
 *
 * @Provider registra este mapper automaticamente no JAX-RS container.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {

        // --- Negócio: agente não habilitado ---
        if (e instanceof AgenteNaoHabilitadoException ex) {
            LOG.warnf("Acesso negado: agente=%d não habilitado.", ex.getCodAgente());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErroDto("AGENTE_NAO_HABILITADO", ex.getMessage()))
                    .build();
        }

        // --- Negócio: dados indisponíveis no Redis ---
        if (e instanceof DadosNaoDisponiveisException ex) {
            LOG.warnf("Cache miss: agente=%d mesAno=%s. Guardian irá restaurar.", ex.getCodAgente(), ex.getMesAno());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "600")  // 10 minutos
                    .entity(new ErroDto("DADOS_NAO_DISPONIVEIS", ex.getMessage()))
                    .build();
        }

        // --- Bean Validation: parâmetros inválidos ---
        if (e instanceof ConstraintViolationException ex) {
            String detalhes = ex.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            LOG.debugf("Validação falhou: %s", detalhes);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErroDto("PARAMETRO_INVALIDO", detalhes))
                    .build();
        }

        // --- Exceções HTTP explícitas (ex: thrown pelo resource) ---
        if (e instanceof WebApplicationException ex) {
            return ex.getResponse();
        }

        // --- Qualquer outro erro não esperado ---
        LOG.errorf(e, "Erro interno não tratado: %s", e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErroDto("ERRO_INTERNO", "Erro interno. Consulte os logs do servidor."))
                .build();
    }
}
