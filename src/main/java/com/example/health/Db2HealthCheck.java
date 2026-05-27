package com.example.health;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Health check de readiness para o banco de dados.
 *
 * Readiness: verifica se o serviço está pronto para receber tráfego.
 * O pod não recebe requisições enquanto este check falhar.
 *
 * Endpoint: GET /q/health/ready
 *
 * A query é configurável via application.properties:
 *   - Dev (H2):  SELECT 1
 *   - Prod (DB2): SELECT 1 FROM SYSIBM.SYSDUMMY1
 */
@ApplicationScoped
@Readiness
public class Db2HealthCheck implements HealthCheck {

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "fgo.db.health-query", defaultValue = "SELECT 1 FROM SYSIBM.SYSDUMMY1")
    String healthQuery;

    @Override
    public HealthCheckResponse call() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(healthQuery)) {

            boolean ok = rs.next();
            return HealthCheckResponse.builder()
                    .name("db")
                    .status(ok)
                    .withData("url", dataSource.getConfiguration().connectionPoolConfiguration()
                            .connectionFactoryConfiguration().jdbcUrl())
                    .build();

        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("db")
                    .down()
                    .withData("erro", e.getMessage())
                    .build();
        }
    }
}
