package com.example.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Configurações técnicas do MS FGO — apenas parâmetros de infraestrutura.
 * Nenhum dado de negócio (agentes, programas, etc.) é configurado aqui.
 */
@ConfigMapping(prefix = "fgo")
public interface FgoConfig {

    Cache cache();

    Auth auth();

    Db db();

    interface Cache {
        /** TTL dos dados no Redis. Padrão: 25h. */
        @WithDefault("PT25H")
        Duration ttlDados();

        /** TTL do lock SET NX por agente. Padrão: 5min. */
        @WithDefault("PT5M")
        Duration ttlLock();
    }

    interface Auth {
        /** Habilita validação JWT. false = dev mode (usa X-Cod-Agente ou default 1). */
        @WithDefault("true")
        boolean habilitado();

        /** Valida assinatura HMAC dos tokens. false = dev mode. */
        @WithDefault("true")
        boolean validarAssinatura();

        /** Segredo HMAC para o token de autorização interno. */
        @WithDefault("fgo-dev-secret-change-in-prod")
        String segredoAutorizacao();
    }

    interface Db {
        /** Query de health check. SELECT 1 em H2, SELECT 1 FROM SYSIBM.SYSDUMMY1 em DB2. */
        @WithDefault("SELECT 1 FROM SYSIBM.SYSDUMMY1")
        String healthQuery();
    }
}
