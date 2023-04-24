package dk.dbc.ess.service.usage;

import java.util.Objects;

/**
 * Class representing a usage log entry with the fields:
 *
 *  <br> databaseId:  ID of external database to which the ESS service provides access, e.g. ArticleFirst
 *  <br> clientId:    ID of client application accessing the external database (OPTIONAL), e.g. zgateway 
 *  <br> agencyId:    ID of library accessing the external database (OPTIONAL), e.g. 010100
 *  <br> recordCount: Number of records presented to the client (OPTIONAL)
 */
public class Usage {
    private String databaseId;
    private String clientId;
    private String agencyId;
    private int recordCount = 0;

    public String getDatabaseId() {
        return databaseId;
    }

    public Usage withDatabaseId(String databaseId) {
        this.databaseId = databaseId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public Usage withClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public Usage withAgencyId(String agencyId) {
        this.agencyId = agencyId;
        return this;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public Usage withRecordCount(int recordCount) {
        this.recordCount = recordCount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Usage usage = (Usage) o;
        return recordCount == usage.recordCount
                && databaseId.equals(usage.databaseId)
                && Objects.equals(clientId, usage.clientId)
                && Objects.equals(agencyId, usage.agencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseId, clientId, agencyId, recordCount);
    }

    @Override
    public String toString() {
        return "Usage{" +
                "databaseId='" + databaseId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", agencyId='" + agencyId + '\'' +
                ", recordCount=" + recordCount +
                '}';
    }
}
