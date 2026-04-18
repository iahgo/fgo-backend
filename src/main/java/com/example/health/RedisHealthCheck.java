package com.example.health;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Health check de liveness para o Redis.
 *
 * Liveness: verifica se o Redis está respondendo. Se falhar, o container
 * é reiniciado pelo Docker/OpenShift (processo pode estar preso).
 *
 * Endpoint: GET /q/health/live
 *
 * O ping é a operação mais leve do Redis — O(1), sem afetar dados.
 */
@ApplicationScoped
@Liveness
public class RedisHealthCheck implements HealthCheck {

    @Inject
    RedisDataSource redis;

    @Override
    public HealthCheckResponse call() {
        try {
            // PING retorna "PONG" — operação mais leve disponível no Redis
            String pong = redis.value(String.class).get("__health_ping__");
            // get de chave inexistente retorna null, mas confirma que o Redis responde
            return HealthCheckResponse.builder()
                    .name("redis")
                    .up()
                    .withData("sentinel", "mymaster")
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("redis")
                    .down()
                    .withData("erro", e.getMessage())
                    .build();
        }
    }
}
