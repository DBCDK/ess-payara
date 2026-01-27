package dk.dbc.ess.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJBException;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Handles database schema evolution at startup using flyway migration
 * <p>
 * Default scan path for migration discovery is classpath:db/migration.
 * </p>
 */
@Startup
@Singleton
public class DatabaseMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrator.class);

    @Resource(lookup = "jdbc/ess_db")
    DataSource dataSource;

    public DatabaseMigrator() {}

    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        if (isDatabaseAccessReadOnly()) {
            LOGGER.info("database access is read-only, no migration attempted");
            return;
        }

        final Flyway flyway = Flyway.configure()
                .table("schema_version")
                .dataSource(dataSource)
                .load();
        for (MigrationInfo info : flyway.info().all()) {
            LOGGER.info("database migration {} : {} from file '{}'",
                    info.getVersion(), info.getDescription(), info.getScript());
        }
        flyway.migrate();
    }

    private boolean isDatabaseAccessReadOnly() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isReadOnly();
        } catch (SQLException e) {
            throw new EJBException("Unable to acquire read-only property", e);
        }
    }
}
