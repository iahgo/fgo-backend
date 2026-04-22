package com.example.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Filtro JAX-RS que registra cada requisição e resposta com:
 *   - [REQ]  método, path, headers relevantes, body (se JSON e < 4KB)
 *   - [RES]  status HTTP, elapsed time em ms, body da resposta (se < 4KB)
 *
 * Cada par req/res compartilha um correlationId para facilitar o rastreamento.
 *
 * Logs em nível INFO — visíveis no /admin/logs e nos logs do pod (oc logs).
 */
@Provider
@Priority(Priorities.USER - 200)
public class RequestResponseLoggingFilter
        implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

    private static final Logger LOG = Logger.getLogger("fgo.access");
    private static final int    MAX_BODY_BYTES = 4096;
    private static final String KEY_START      = "fgo.req.start";
    private static final String KEY_CID        = "fgo.req.cid";
    private static final String KEY_METHOD     = "fgo.req.method";
    private static final String KEY_PATH       = "fgo.req.path";

    // Headers sensíveis que não devem aparecer no log
    private static final List<String> HEADERS_BLOQUEADOS = List.of(
            "authorization", "x-auth-token", "cookie", "set-cookie"
    );

    @Inject
    ObjectMapper mapper;

    // =========================================================================
    // 1. REQUISIÇÃO — loga entrada e captura body se possível
    // =========================================================================

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        String cid    = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String method = req.getMethod();
        String path   = req.getUriInfo().getRequestUri().getPath();

        req.setProperty(KEY_START,  System.currentTimeMillis());
        req.setProperty(KEY_CID,    cid);
        req.setProperty(KEY_METHOD, method);
        req.setProperty(KEY_PATH,   path);

        String agente = req.getHeaderString("X-Cod-Agente");
        String agenteLog = agente != null ? " agente=" + agente : "";

        // Captura body (POST/PUT/PATCH) sem consumir o stream original
        String bodyLog = "";
        if (req.hasEntity() && req.getMediaType() != null
                && req.getMediaType().toString().contains("json")) {
            byte[] bytes = req.getEntityStream().readNBytes(MAX_BODY_BYTES);
            req.setEntityStream(new ByteArrayInputStream(bytes));
            if (bytes.length > 0) {
                bodyLog = " body=" + new String(bytes, StandardCharsets.UTF_8);
            }
        }

        LOG.infof("[REQ][%s] %s %s%s%s", cid, method, path, agenteLog, bodyLog);
    }

    // =========================================================================
    // 2. RESPOSTA — loga status e elapsed time
    // =========================================================================

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        Long start  = (Long)   req.getProperty(KEY_START);
        String cid  = (String) req.getProperty(KEY_CID);
        String method = (String) req.getProperty(KEY_METHOD);
        String path   = (String) req.getProperty(KEY_PATH);

        if (start == null || cid == null) return;

        long elapsed = System.currentTimeMillis() - start;
        int  status  = res.getStatus();

        // Serializa a entidade da resposta (objeto Java antes do Jackson) se pequena
        String bodyLog = "";
        Object entity  = res.getEntity();
        if (entity != null) {
            try {
                String json = mapper.writeValueAsString(entity);
                if (json.length() <= MAX_BODY_BYTES) {
                    bodyLog = " body=" + json;
                } else {
                    bodyLog = " body=[" + json.length() + " chars — truncado]";
                }
            } catch (Exception ignored) {
                bodyLog = " body=[não serializável]";
            }
        }

        String nivel = status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO";
        LOG.infof("[RES][%s] %s %s → HTTP %d em %dms%s",
                cid, method, path, status, elapsed, bodyLog);

        if (elapsed > 5_000) {
            LOG.warnf("[SLOW][%s] %s %s demorou %dms", cid, method, path, elapsed);
        }
    }

    // =========================================================================
    // 3. WriterInterceptor — não usado para body capture aqui
    //    (usamos res.getEntity() no filter acima que é mais simples)
    // =========================================================================

    @Override
    public void aroundWriteTo(WriterInterceptorContext ctx) throws IOException {
        ctx.proceed();
    }
}
