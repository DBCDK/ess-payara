package dk.dbc.ess.service.usage;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Enterprise Java Bean responsible for persisting usage logs
 */
@Stateless
public class UsageLogger {

    @Resource(lookup = "jdbc/ess_db")
    DataSource dataSource;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void log(Usage usage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insertStatement = connection.prepareStatement(
                     "INSERT INTO usage_log(database_id, client_id, agency_id, record_count) VALUES (?, ?, ?, ?)")) {
            insertStatement.setString(1, usage.getDatabaseId());
            insertStatement.setString(2, usage.getClientId());
            insertStatement.setString(3, usage.getAgencyId());
            insertStatement.setInt(4, usage.getRecordCount());
            insertStatement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("error persisting usage log entry %s", usage), e);
        }
    }
}
