package dk.dbc.ess.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import dk.dbc.commons.testcontainers.postgres.DBCPostgreSQLContainer;
import dk.dbc.ess.service.usage.Usage;
import dk.dbc.httpclient.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class ContainerTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerTestBase.class);

    static final WireMockServer wireMockServer;
    static final DBCPostgreSQLContainer essDbContainer;
    static final GenericContainer serviceContainer;
    static final String serviceBaseUrl;
    static final HttpClient httpClient;

    static {
        wireMockServer = startWiremockServer();
        essDbContainer = startEssDbContainer();
        serviceContainer = startServiceContainer();
        serviceBaseUrl = String.format("http://%s:%d", serviceContainer.getHost(),
                serviceContainer.getMappedPort(8080));
        httpClient = HttpClient.create(HttpClient.newClient());
    }

    static Connection getEssDbConnection() {
        // Get database connection for use outside of docker containers, e.g. directly from tests
        try {
            final Connection connection = essDbContainer.createConnection();
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Usage> getUsageByClientId(String clientId) {
        try (Connection connection = getEssDbConnection()) {
            final PreparedStatement selectStatement;
            if (clientId == null) {
                selectStatement = connection.prepareStatement(
                        "SELECT * from usage_log WHERE client_id IS NULL ORDER BY logged_at ASC");
            } else {
                selectStatement = connection.prepareStatement(
                        "SELECT * from usage_log WHERE client_id=? ORDER BY logged_at ASC");
                selectStatement.setString(1, clientId);
            }
            final ResultSet resultSet = selectStatement.executeQuery();
            final List<Usage> loggedUsage = new ArrayList<>();
            while (resultSet.next()) {
                final Timestamp loggedAt = resultSet.getTimestamp("LOGGED_AT");
                assertThat("usage logged_at", loggedAt, is(notNullValue()));
                loggedUsage.add(new Usage()
                        .withDatabaseId(resultSet.getString("DATABASE_ID"))
                        .withClientId(resultSet.getString("CLIENT_ID"))
                        .withAgencyId(resultSet.getString("AGENCY_ID"))
                        .withRecordCount(resultSet.getInt("RECORD_COUNT")));
            }
            return loggedUsage;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static WireMockServer startWiremockServer() {
        final WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        Testcontainers.exposeHostPorts(wireMockServer.port());
        LOGGER.info("Wiremock server at port:{}", wireMockServer.port());
        return wireMockServer;
    }

    private static DBCPostgreSQLContainer startEssDbContainer() {
        final DBCPostgreSQLContainer dbcPostgreSQLContainer = new DBCPostgreSQLContainer();
        dbcPostgreSQLContainer.start();
        dbcPostgreSQLContainer.exposeHostPort();
        LOGGER.info(dbcPostgreSQLContainer.getJdbcUrl());
        return dbcPostgreSQLContainer;
    }

    private static GenericContainer startServiceContainer() {
        final GenericContainer serviceContainer;
        try {
            final String wireMockServerUrl = "http://host.testcontainers.internal:" + wireMockServer.port();
            serviceContainer = new GenericContainer(new String(Files.readAllBytes(Paths.get("docker.out")), StandardCharsets.UTF_8))
                    .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                    .withEnv("JAVA_MAX_HEAP_SIZE", "2G")
                    .withEnv("LOG_FORMAT", "text")
                    .withEnv("ESS_DB_URL", essDbContainer.getPayaraDockerJdbcUrl())
                    .withEnv("META_PROXY_URL", wireMockServerUrl)
                    .withEnv("OPEN_FORMAT_URL", wireMockServerUrl + "/api/v1/format")
                    .withEnv("BASES", "libris,bibsys")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/openapi"))
                    .withStartupTimeout(Duration.ofMinutes(2));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serviceContainer.start();
        return serviceContainer;
    }
}
