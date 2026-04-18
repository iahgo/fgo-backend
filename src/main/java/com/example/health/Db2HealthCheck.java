package com.example.health;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Health check de readiness para o DB2.
 *
 * Readiness: verifica se o serviço está pronto para receber tráfego.
 * O pod não recebe requisições enquanto este check falhar.
 *
 * Endpoint: GET /q/health/ready
 *
 * SYSIBM.SYSDUMMY1 é a tabela de sistema do DB2 equivalente ao
 * Oracle DUAL — sempre retorna 1 linha, usada para verificar conectividade.
 *
 * Nota: este check toca o DB2, mas apenas no contexto de health check
 * (chamado pelo Docker, não pelo usuário). Não viola a regra
 * "DB2 nunca no caminho do usuário".
 */
@ApplicationScoped
@Readiness
public class Db2HealthCheck implements HealthCheck {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1")) {

            boolean ok = rs.next();
            return HealthCheckResponse.builder()
                    .name("db2")
                    .status(ok)
                    .withData("url", dataSource.getConfiguration().connectionPoolConfiguration()
                            .connectionFactoryConfiguration().jdbcUrl())
                    .build();

        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("db2")
                    .down()
                    .withData("erro", e.getMessage())
                    .build();
        }
    }
}
