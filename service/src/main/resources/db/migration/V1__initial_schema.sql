CREATE TABLE usage_log (
    database_id     TEXT NOT NULL,
    client_id       TEXT,
    agency_id       TEXT,
    record_count    INTEGER NOT NULL,
    logged_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX usage_log_database_id ON usage_log(database_id);
CREATE INDEX usage_log_client_id ON usage_log(client_id);
CREATE INDEX usage_log_logged_at ON usage_log(logged_at);