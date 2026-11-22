package com.leonardogalvao.pontocontrole;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PontoControleApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ponto_test")
            .withUsername("ponto_test")
            .withPassword("ponto_test");

    private static final Path PHOTO_STORAGE = Path.of("target", "test-photos").toAbsolutePath();

    @DynamicPropertySource
    static void configureApplication(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.photo-storage.path", PHOTO_STORAGE::toString);
    }

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    @Test
    void startsTheApplicationAndAppliesTheInitialMigration() {
        assertThat(applicationContext).isNotNull();

        Integer migratedTables = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'employees', 'attendance_records')
                """, Integer.class);

        Integer successfulVersionOne = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """, Integer.class);

        Integer requiredIndexes = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'ux_users_single_manager',
                      'ix_attendance_employee_recorded_at',
                      'ix_attendance_work_date_recorded_at'
                  )
                """, Integer.class);

        assertThat(migratedTables).isEqualTo(3);
        assertThat(successfulVersionOne).isEqualTo(1);
        assertThat(requiredIndexes).isEqualTo(3);
    }

    @Test
    void readinessChecksDatabaseAndPhotoStorageAndPropagatesRequestId() throws Exception {
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .header("X-Request-Id", requestId)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.headers().firstValue("X-Request-Id")).contains(requestId);
    }
}
