package com.example.security;

import com.example.dto.ErroDto;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;

/**
 * Filtro de autenticação e autorização JWT.
 *
 * Lê dois headers:
 *   X-Token-Atendimento — JWT corporativo (GATEAU): valida sessão ativa
 *   X-Token-Autorizacao — JWT interno: extrai cdAgtFnco + funcionalidades
 *
 * Endpoints /q/* (health, swagger) e /admin/* são isentos.
 * Em dev com fgo.auth.habilitado=false: usa X-Cod-Agente ou default=1.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(SecurityFilter.class);

    @Context
    ResourceInfo resourceInfo;

    @Inject
    TokenService tokenService;

    @Inject
    ContextoSeguranca contexto;

    @ConfigProperty(name = "fgo.auth.habilitado", defaultValue = "true")
    boolean authHabilitado;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();

        if (path.startsWith("/q/") || path.startsWith("q/")
                || path.startsWith("/admin/") || path.startsWith("admin/")) {
            return;
        }

        if (!authHabilitado) {
            String headerAgente = ctx.getHeaderString("X-Cod-Agente");
            int cdAgtFnco = 1;
            if (headerAgente != null && !headerAgente.isBlank()) {
                try { cdAgtFnco = Integer.parseInt(headerAgente.trim()); } catch (NumberFormatException ignored) {}
            }
            contexto.setCdAgtFnco(cdAgtFnco);
            LOG.debugf("[SECURITY] Auth desabilitado. cdAgtFnco=%d", cdAgtFnco);
            return;
        }

        tokenService.validarAtendimento(ctx.getHeaderString("X-Token-Atendimento"));

        TokenService.ClaimsAutorizacao claims = tokenService.validarAutorizacao(ctx.getHeaderString("X-Token-Autorizacao"));
        contexto.setCdAgtFnco(claims.cdAgtFnco());
        contexto.setFuncionalidades(claims.funcionalidades());

        Method metodo = resourceInfo.getResourceMethod();
        if (metodo != null && metodo.isAnnotationPresent(Funcionalidade.class)) {
            String necessaria = metodo.getAnnotation(Funcionalidade.class).value();
            if (!contexto.possui(necessaria)) {
                LOG.warnf("[SECURITY] Acesso negado. cdAgtFnco=%d funcionalidade=%s",
                        claims.cdAgtFnco(), necessaria);
                ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErroDto("FUNCIONALIDADE_NEGADA",
                                "Funcionalidade '" + necessaria + "' não autorizada para este agente."))
                        .type(MediaType.APPLICATION_JSON)
                        .build());
            }
        }
    }
}
