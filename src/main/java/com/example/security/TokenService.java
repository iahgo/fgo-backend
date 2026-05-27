package com.example.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Serviço de parsing e validação dos dois tokens JWT do sistema:
 *
 * 1. Token de Atendimento (GATEAU): JWT corporativo do Banco do Brasil.
 *    Valida que o login do usuário ainda é válido (não expirou).
 *    Header: X-Token-Atendimento
 *
 * 2. Token de Autorização (interno): JWT gerado pela nossa solução de autorização.
 *    Contém cdAgtFnco e lista de funcionalidades autorizadas.
 *    Header: X-Token-Autorizacao
 *    Claims: { "sub": "1", "cdAgtFnco": 1, "funcionalidades": ["PAINEL_INFO_GERAIS", ...], "exp": 9999999999 }
 *
 * Em dev (fgo.auth.validar-assinatura=false): apenas parseia e verifica expiração.
 * Em prod: valida assinatura HMAC-SHA256 do token de autorização.
 */
@ApplicationScoped
public class TokenService {

    private static final Logger LOG = Logger.getLogger(TokenService.class);
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "fgo.auth.validar-assinatura", defaultValue = "true")
    boolean validarAssinatura;

    @ConfigProperty(name = "fgo.auth.segredo-autorizacao", defaultValue = "fgo-dev-secret-change-in-prod")
    String segredoAutorizacao;

    public void validarAtendimento(String token) {
        if (token == null || token.isBlank()) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_ATENDIMENTO_AUSENTE",
                    "Header X-Token-Atendimento obrigatório.");
        }
        JsonNode payload = extrairPayload(token);
        validarExpiry(payload, "Token de atendimento");
        LOG.debugf("[SECURITY] Token atendimento válido. sub=%s", payload.path("sub").asText("?"));
    }

    public ClaimsAutorizacao validarAutorizacao(String token) {
        if (token == null || token.isBlank()) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_AUTORIZACAO_AUSENTE",
                    "Header X-Token-Autorizacao obrigatório.");
        }
        if (validarAssinatura) {
            verificarAssinaturaHmac(token, segredoAutorizacao);
        }
        JsonNode payload = extrairPayload(token);
        validarExpiry(payload, "Token de autorização");

        int cdAgtFnco = payload.path("cdAgtFnco").asInt(0);
        if (cdAgtFnco <= 0) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_AGENTE_INVALIDO",
                    "Claim cdAgtFnco ausente ou inválido no token de autorização.");
        }

        List<String> funcionalidades = new ArrayList<>();
        JsonNode funcs = payload.path("funcionalidades");
        if (funcs.isArray()) {
            funcs.forEach(n -> funcionalidades.add(n.asText()));
        }

        LOG.debugf("[SECURITY] Token autorização válido. cdAgtFnco=%d funcionalidades=%s",
                cdAgtFnco, funcionalidades);
        return new ClaimsAutorizacao(cdAgtFnco, funcionalidades);
    }

    private JsonNode extrairPayload(String token) {
        String[] partes = token.split("\\.");
        if (partes.length < 2) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_MALFORMADO", "JWT inválido: formato incorreto.");
        }
        try {
            byte[] payloadBytes = B64.decode(padBase64(partes[1]));
            return objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_MALFORMADO", "JWT inválido: payload não decodificável.");
        }
    }

    private void validarExpiry(JsonNode payload, String nome) {
        JsonNode expNode = payload.path("exp");
        if (!expNode.isMissingNode() && !expNode.isNull()) {
            long exp = expNode.asLong();
            if (Instant.now().getEpochSecond() > exp) {
                throw erro(Response.Status.UNAUTHORIZED, "TOKEN_EXPIRADO", nome + " expirado.");
            }
        }
    }

    private void verificarAssinaturaHmac(String token, String segredo) {
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_MALFORMADO", "JWT inválido: esperado header.payload.signature.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] assinatura = mac.doFinal((partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8));
            String esperada = Base64.getUrlEncoder().withoutPadding().encodeToString(assinatura);
            if (!esperada.equals(partes[2])) {
                throw erro(Response.Status.UNAUTHORIZED, "TOKEN_ASSINATURA_INVALIDA", "Assinatura JWT inválida.");
            }
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw erro(Response.Status.UNAUTHORIZED, "TOKEN_ERRO_ASSINATURA", "Erro ao verificar assinatura JWT.");
        }
    }

    private String padBase64(String base64url) {
        int pad = base64url.length() % 4;
        if (pad == 2) return base64url + "==";
        if (pad == 3) return base64url + "=";
        return base64url;
    }

    private WebApplicationException erro(Response.Status status, String codigo, String mensagem) {
        return new WebApplicationException(
            Response.status(status)
                .entity(new com.example.dto.ErroDto(codigo, mensagem))
                .type(MediaType.APPLICATION_JSON)
                .build()
        );
    }

    public record ClaimsAutorizacao(int cdAgtFnco, List<String> funcionalidades) {}
}
