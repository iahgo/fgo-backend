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

    interface Cache {
        /** TTL dos dados no Redis. Padrão: 25h. */
        @WithDefault("PT25H")
        Duration ttlDados();

        /** TTL do lock SET NX por agente. Padrão: 5min. */
        @WithDefault("PT5M")
        Duration ttlLock();
    }
}
