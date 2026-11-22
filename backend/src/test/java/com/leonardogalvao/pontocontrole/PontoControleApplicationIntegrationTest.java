package com.leonardogalvao.pontocontrole;

import com.leonardogalvao.pontocontrole.auth.ManagerBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PontoControleApplicationIntegrationTest {

    private static final String MANAGER_EMAIL = "gerente@empresa.local";
    private static final String MANAGER_PASSWORD = "Gerente#Teste2026";
    private static final String JWT_SECRET_BASE64 = Base64.getEncoder().encodeToString(
            "segredo-de-testes-com-mais-de-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));

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
        registry.add("app.bootstrap-manager.email", () -> MANAGER_EMAIL);
        registry.add("app.bootstrap-manager.password", () -> MANAGER_PASSWORD);
        registry.add("app.jwt.secret-base64", () -> JWT_SECRET_BASE64);
    }

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ManagerBootstrapService managerBootstrapService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    ObjectMapper objectMapper;

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

    @Test
    void bootstrapsTheManagerOnlyOnceAndNeverStoresThePlainPassword() {
        ManagerSnapshot before = jdbcTemplate.queryForObject("""
                SELECT id, email, password_hash
                FROM users
                WHERE role = 'GERENTE'
                """, (resultSet, rowNumber) -> new ManagerSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash")));

        managerBootstrapService.bootstrap();

        ManagerSnapshot after = jdbcTemplate.queryForObject("""
                SELECT id, email, password_hash
                FROM users
                WHERE role = 'GERENTE'
                """, (resultSet, rowNumber) -> new ManagerSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash")));
        Integer managerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'GERENTE'",
                Integer.class);

        assertThat(after).isEqualTo(before);
        assertThat(managerCount).isEqualTo(1);
        assertThat(after.email()).isEqualTo(MANAGER_EMAIL);
        assertThat(after.passwordHash()).isNotEqualTo(MANAGER_PASSWORD);
        assertThat(passwordEncoder.matches(MANAGER_PASSWORD, after.passwordHash())).isTrue();
    }

    @Test
    void managerCanLoginAndReadMeOnlyWithAnAccessToken() throws Exception {
        HttpResponse<String> login = postJson(
                "/api/v1/auth/login",
                """
                        {"email":"  GERENTE@EMPRESA.LOCAL  ","password":"Gerente#Teste2026"}
                        """,
                null,
                UUID.randomUUID().toString());

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(login.headers().firstValue("Pragma")).contains("no-cache");
        JsonNode body = objectMapper.readTree(login.body());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("tokenPurpose").asText()).isEqualTo("ACCESS");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(1800);
        assertThat(body.get("role").asText()).isEqualTo("GERENTE");
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();

        String token = body.get("token").asText();
        HttpResponse<String> me = get("/api/v1/me", token, UUID.randomUUID().toString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(me.body()).get("email").asText()).isEqualTo(MANAGER_EMAIL);

        HttpResponse<String> forbiddenChange = postJson(
                "/api/v1/auth/change-password",
                """
                        {"currentPassword":"Gerente#Teste2026","newPassword":"OutraSenha#2026"}
                        """,
                token,
                UUID.randomUUID().toString());
        assertThat(forbiddenChange.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbiddenChange.body()).get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void invalidCredentialsAndMissingTokenReturnSafeCorrelatedErrors() throws Exception {
        String loginRequestId = UUID.randomUUID().toString();
        HttpResponse<String> login = postJson(
                "/api/v1/auth/login",
                """
                        {"email":"gerente@empresa.local","password":"SenhaIncorreta#2026"}
                        """,
                null,
                loginRequestId);

        assertThat(login.statusCode()).isEqualTo(401);
        JsonNode loginError = objectMapper.readTree(login.body());
        assertThat(loginError.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(loginError.get("requestId").asText()).isEqualTo(loginRequestId);
        assertThat(login.body()).doesNotContain("SenhaIncorreta");

        String meRequestId = UUID.randomUUID().toString();
        HttpResponse<String> me = get("/api/v1/me", null, meRequestId);
        assertThat(me.statusCode()).isEqualTo(401);
        JsonNode meError = objectMapper.readTree(me.body());
        assertThat(meError.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(meError.get("requestId").asText()).isEqualTo(meRequestId);
    }

    @Test
    void employeeMustChangeTheTemporaryPasswordBeforeReceivingAccess() throws Exception {
        UUID employeeId = UUID.randomUUID();
        String suffix = employeeId.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        String email = "funcionario-" + suffix.toLowerCase(java.util.Locale.ROOT) + "@empresa.local";
        String temporaryPassword = "Temporaria#2026";
        String newPassword = "Definitiva#2026";

        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, role, must_change_password)
                VALUES (?, ?, ?, 'FUNCIONARIO', true)
                """, employeeId, email, passwordEncoder.encode(temporaryPassword));
        jdbcTemplate.update("""
                INSERT INTO employees (user_id, registration_number, name, job_title, profile_photo_key)
                VALUES (?, ?, 'Funcionário Teste', 'Analista', ?)
                """, employeeId, "TEST-" + suffix, "profiles/" + employeeId + ".jpg");

        HttpResponse<String> firstLogin = postJson(
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}",
                null,
                UUID.randomUUID().toString());
        assertThat(firstLogin.statusCode()).isEqualTo(200);
        JsonNode firstLoginBody = objectMapper.readTree(firstLogin.body());
        assertThat(firstLoginBody.get("tokenPurpose").asText()).isEqualTo("PASSWORD_CHANGE");
        assertThat(firstLoginBody.get("expiresIn").asLong()).isEqualTo(600);
        assertThat(firstLoginBody.get("mustChangePassword").asBoolean()).isTrue();
        String changeToken = firstLoginBody.get("token").asText();

        HttpResponse<String> blockedMe = get("/api/v1/me", changeToken, UUID.randomUUID().toString());
        assertThat(blockedMe.statusCode()).isEqualTo(403);

        HttpResponse<String> change = postJson(
                "/api/v1/auth/change-password",
                "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"" + newPassword + "\"}",
                changeToken,
                UUID.randomUUID().toString());
        assertThat(change.statusCode()).isEqualTo(204);

        HttpResponse<String> reuse = postJson(
                "/api/v1/auth/change-password",
                "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"" + newPassword + "\"}",
                changeToken,
                UUID.randomUUID().toString());
        assertThat(reuse.statusCode()).isEqualTo(409);

        HttpResponse<String> oldPasswordLogin = postJson(
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}",
                null,
                UUID.randomUUID().toString());
        assertThat(oldPasswordLogin.statusCode()).isEqualTo(401);

        HttpResponse<String> secondLogin = postJson(
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + newPassword + "\"}",
                null,
                UUID.randomUUID().toString());
        assertThat(secondLogin.statusCode()).isEqualTo(200);
        JsonNode secondLoginBody = objectMapper.readTree(secondLogin.body());
        assertThat(secondLoginBody.get("tokenPurpose").asText()).isEqualTo("ACCESS");
        assertThat(secondLoginBody.get("mustChangePassword").asBoolean()).isFalse();

        HttpResponse<String> me = get(
                "/api/v1/me",
                secondLoginBody.get("token").asText(),
                UUID.randomUUID().toString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(me.body()).get("id").asText()).isEqualTo(employeeId.toString());
    }

    @Test
    void rejectsSignedTokensWithWrongAudiencePurposeOrExpiration() throws Exception {
        UUID managerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE role = 'GERENTE'",
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("controle-ponto-local")
                .audience(List.of("audiencia-incorreta"))
                .subject(managerId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "GERENTE")
                .claim("purpose", "ACCESS")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        claims))
                .getTokenValue();

        assertUnauthorized(token);

        JwtClaimsSet invalidPurposeClaims = JwtClaimsSet.builder()
                .issuer("controle-ponto-local")
                .audience(List.of("controle-ponto-android"))
                .subject(managerId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "GERENTE")
                .claim("purpose", "ADMIN")
                .build();
        String invalidPurposeToken = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        invalidPurposeClaims))
                .getTokenValue();
        assertUnauthorized(invalidPurposeToken);

        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("controle-ponto-local")
                .audience(List.of("controle-ponto-android"))
                .subject(managerId.toString())
                .issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(120))
                .claim("role", "GERENTE")
                .claim("purpose", "ACCESS")
                .build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        expiredClaims))
                .getTokenValue();
        assertUnauthorized(expiredToken);

        JwtClaimsSet missingExpirationClaims = JwtClaimsSet.builder()
                .issuer("controle-ponto-local")
                .audience(List.of("controle-ponto-android"))
                .subject(managerId.toString())
                .issuedAt(now)
                .claim("role", "GERENTE")
                .claim("purpose", "ACCESS")
                .build();
        String missingExpirationToken = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        missingExpirationClaims))
                .getTokenValue();
        assertUnauthorized(missingExpirationToken);
    }

    @Test
    void clientRoutingErrorsKeepTheirHttpStatusAndCorrelationId() throws Exception {
        String accessToken = managerAccessToken();

        String notFoundRequestId = UUID.randomUUID().toString();
        HttpResponse<String> notFound = get(
                "/api/v1/recurso-inexistente",
                accessToken,
                notFoundRequestId);
        assertError(notFound, 404, "NOT_FOUND", notFoundRequestId);

        String methodRequestId = UUID.randomUUID().toString();
        HttpResponse<String> methodNotAllowed = postJson(
                "/api/v1/me",
                "{}",
                accessToken,
                methodRequestId);
        assertError(methodNotAllowed, 405, "METHOD_NOT_ALLOWED", methodRequestId);

        String mediaTypeRequestId = UUID.randomUUID().toString();
        HttpResponse<String> unsupportedMediaType = post(
                "/api/v1/auth/login",
                "email=gerente@empresa.local",
                "text/plain",
                null,
                mediaTypeRequestId);
        assertError(unsupportedMediaType, 415, "UNSUPPORTED_MEDIA_TYPE", mediaTypeRequestId);
    }

    @Test
    void managerCreatesAndListsEmployeeWhoCanReadOnlyTheOwnProfilePhoto() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "funcionario-" + suffix + "@empresa.local";
        String registration = "CAD-" + suffix.toUpperCase(java.util.Locale.ROOT);
        String temporaryPassword = "Temporaria#2026";
        String newPassword = "Definitiva#2026";

        HttpResponse<String> created = postEmployee(
                """
                        {
                          "name":"  Ana Demonstração  ",
                          "registrationNumber":"  %s  ",
                          "jobTitle":"  Analista de Operações  ",
                          "email":"  %s  ",
                          "temporaryPassword":"%s"
                        }
                        """.formatted(registration.toLowerCase(java.util.Locale.ROOT), email.toUpperCase(), temporaryPassword),
                jpeg(),
                "image/jpeg",
                managerAccessToken(),
                UUID.randomUUID().toString());

        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode createdBody = objectMapper.readTree(created.body());
        UUID employeeId = UUID.fromString(createdBody.get("id").asText());
        assertThat(createdBody.get("email").asText()).isEqualTo(email);
        assertThat(createdBody.get("registrationNumber").asText()).isEqualTo(registration);
        assertThat(createdBody.get("name").asText()).isEqualTo("Ana Demonstração");
        assertThat(createdBody.get("todayStatus").asText()).isEqualTo("SEM_REGISTRO");
        assertThat(created.body()).doesNotContain(temporaryPassword);
        assertThat(Files.isRegularFile(PHOTO_STORAGE.resolve("profiles").resolve(employeeId + ".jpg"))).isTrue();

        HttpResponse<String> employees = get(
                "/api/v1/employees",
                managerAccessToken(),
                UUID.randomUUID().toString());
        assertThat(employees.statusCode()).isEqualTo(200);
        assertThat(employees.body()).contains(email, registration, "\"enteredToday\":0");

        HttpResponse<String> firstLogin = postJson(
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}",
                null,
                UUID.randomUUID().toString());
        String changeToken = objectMapper.readTree(firstLogin.body()).get("token").asText();
        HttpResponse<String> change = postJson(
                "/api/v1/auth/change-password",
                "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"" + newPassword + "\"}",
                changeToken,
                UUID.randomUUID().toString());
        assertThat(change.statusCode()).isEqualTo(204);

        HttpResponse<String> secondLogin = postJson(
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + newPassword + "\"}",
                null,
                UUID.randomUUID().toString());
        String employeeToken = objectMapper.readTree(secondLogin.body()).get("token").asText();

        HttpResponse<String> me = get("/api/v1/me", employeeToken, UUID.randomUUID().toString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(
                "Ana Demonstração",
                registration,
                "Analista de Operações",
                "/api/v1/employees/" + employeeId + "/photo",
                "SEM_REGISTRO");

        HttpResponse<byte[]> ownPhoto = getBytes(
                "/api/v1/employees/" + employeeId + "/photo",
                employeeToken,
                UUID.randomUUID().toString());
        assertThat(ownPhoto.statusCode()).isEqualTo(200);
        assertThat(ownPhoto.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(ownPhoto.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(ownPhoto.body()).isEqualTo(jpeg());

        HttpResponse<String> forbiddenList = get(
                "/api/v1/employees",
                employeeToken,
                UUID.randomUUID().toString());
        assertThat(forbiddenList.statusCode()).isEqualTo(403);

        HttpResponse<byte[]> hiddenPhoto = getBytes(
                "/api/v1/employees/" + UUID.randomUUID() + "/photo",
                employeeToken,
                UUID.randomUUID().toString());
        assertThat(hiddenPhoto.statusCode()).isEqualTo(404);
    }

    @Test
    void duplicateOrInvalidEmployeeDataDoesNotLeaveAProfilePhoto() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "duplicidade-" + suffix + "@empresa.local";
        String registration = "DUP-" + suffix.toUpperCase(java.util.Locale.ROOT);
        String managerToken = managerAccessToken();
        String metadata = employeeMetadata("Pessoa Duplicada", registration, "Assistente", email);

        HttpResponse<String> created = postEmployee(
                metadata,
                jpeg(),
                "image/jpeg",
                managerToken,
                UUID.randomUUID().toString());
        assertThat(created.statusCode()).isEqualTo(201);
        long photosBeforeRejections = profilePhotoCount();

        HttpResponse<String> duplicateEmail = postEmployee(
                employeeMetadata("Outro Nome", "OUT-" + suffix.toUpperCase(java.util.Locale.ROOT), "Assistente", email),
                jpeg(),
                "image/jpeg",
                managerToken,
                UUID.randomUUID().toString());
        assertThat(duplicateEmail.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(duplicateEmail.body()).get("code").asText()).isEqualTo("DUPLICATE_EMAIL");

        HttpResponse<String> duplicateRegistration = postEmployee(
                employeeMetadata("Outro Nome", registration, "Assistente", "outro-" + suffix + "@empresa.local"),
                jpeg(),
                "image/jpeg",
                managerToken,
                UUID.randomUUID().toString());
        assertThat(duplicateRegistration.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(duplicateRegistration.body()).get("code").asText())
                .isEqualTo("DUPLICATE_REGISTRATION");

        HttpResponse<String> invalidPhoto = postEmployee(
                employeeMetadata(
                        "Foto Inválida",
                        "IMG-" + suffix.toUpperCase(java.util.Locale.ROOT),
                        "Assistente",
                        "imagem-" + suffix + "@empresa.local"),
                "isto não é uma imagem".getBytes(StandardCharsets.UTF_8),
                "image/jpeg",
                managerToken,
                UUID.randomUUID().toString());
        assertThat(invalidPhoto.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(invalidPhoto.body()).get("code").asText()).isEqualTo("INVALID_PHOTO");
        assertThat(profilePhotoCount()).isEqualTo(photosBeforeRejections);
    }

    private String managerAccessToken() throws Exception {
        HttpResponse<String> login = postJson(
                "/api/v1/auth/login",
                """
                        {"email":"gerente@empresa.local","password":"Gerente#Teste2026"}
                        """,
                null,
                UUID.randomUUID().toString());
        assertThat(login.statusCode()).isEqualTo(200);
        return objectMapper.readTree(login.body()).get("token").asText();
    }

    private void assertError(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedCode,
            String expectedRequestId) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(expectedCode);
        assertThat(body.get("requestId").asText()).isEqualTo(expectedRequestId);
        assertThat(body.get("message").asText()).isNotBlank();
    }

    private void assertUnauthorized(String token) throws Exception {
        HttpResponse<String> response = get("/api/v1/me", token, UUID.randomUUID().toString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    private HttpResponse<String> postJson(String path, String body, String token, String requestId)
            throws Exception {
        return post(path, body, "application/json", token, requestId);
    }

    private HttpResponse<String> post(
            String path,
            String body,
            String contentType,
            String token,
            String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", contentType)
                .header("X-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        authorize(builder, token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Request-Id", requestId)
                .GET();
        authorize(builder, token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Request-Id", requestId)
                .GET();
        authorize(builder, token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> postEmployee(
            String metadata,
            byte[] photo,
            String photoContentType,
            String token,
            String requestId) throws Exception {
        String boundary = "PontoControle-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"metadata\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + metadata + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"photo\"; filename=\"profile.jpg\"\r\n"
                + "Content-Type: " + photoContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(photo);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/employees"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        authorize(builder, token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String employeeMetadata(
            String name,
            String registration,
            String jobTitle,
            String email) {
        return """
                {
                  "name":"%s",
                  "registrationNumber":"%s",
                  "jobTitle":"%s",
                  "email":"%s",
                  "temporaryPassword":"Temporaria#2026"
                }
                """.formatted(name, registration, jobTitle, email);
    }

    private byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x1f4f78);
        image.setRGB(1, 0, 0xffffff);
        image.setRGB(0, 1, 0x202b33);
        image.setRGB(1, 1, 0x54a36f);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpg", output)).isTrue();
        return output.toByteArray();
    }

    private long profilePhotoCount() throws Exception {
        Path profiles = PHOTO_STORAGE.resolve("profiles");
        if (!Files.isDirectory(profiles)) {
            return 0;
        }
        try (var paths = Files.list(profiles)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private void authorize(HttpRequest.Builder builder, String token) {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private record ManagerSnapshot(UUID id, String email, String passwordHash) {
    }
}
