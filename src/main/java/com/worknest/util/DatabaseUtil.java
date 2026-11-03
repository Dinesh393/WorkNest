package com.worknest.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.logging.Logger;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/** Central, TLS-enforced MySQL connection pool for WorkNest. */
public final class DatabaseUtil {
    private static final Logger LOGGER = Logger.getLogger(DatabaseUtil.class.getName());
    private static final String MYSQL_PREFIX = "jdbc:mysql://";
    private static final Pattern REQUIRED_TLS = Pattern.compile(
            "(?:^|&)sslmode=required(?:&|$)", Pattern.CASE_INSENSITIVE);
    private static volatile DataSource pool;

    private DatabaseUtil() {
    }

    public static Connection getConnection() throws SQLException {
        DataSource dataSource = getOrCreatePool();
        long startedAt = System.nanoTime();
        try {
            Connection connection = dataSource.getConnection();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (elapsedMs >= 100L) {
                LOGGER.info("PERF operation=db-connection outcome=borrowed durationMs="
                        + elapsedMs + " active=" + dataSource.getActive()
                        + " idle=" + dataSource.getIdle());
            }
            return connection;
        } catch (SQLException exception) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            LOGGER.info("PERF operation=db-connection outcome=failed durationMs="
                    + elapsedMs);
            throw new SQLException("Could not connect to the configured WorkNest database.", exception);
        }
    }

    private static DataSource getOrCreatePool() throws SQLException {
        DataSource current = pool;
        if (current != null) return current;

        synchronized (DatabaseUtil.class) {
            current = pool;
            if (current != null) return current;

            String url = environmentValue("DB_URL");
            String username = environmentValue("DB_USERNAME");
            String password = environmentValue("DB_PASSWORD");
            if (url == null || username == null || password == null) {
                throw new SQLException(
                        "Database configuration is incomplete. Set DB_URL, DB_USERNAME, and DB_PASSWORD.");
            }

            String normalizedUrl = url.trim();
            if (!normalizedUrl.startsWith(MYSQL_PREFIX)) {
                throw new SQLException("DB_URL must be a MySQL JDBC URL.");
            }
            int queryStart = normalizedUrl.indexOf('?');
            String query = queryStart < 0 ? "" : normalizedUrl.substring(queryStart + 1);
            if (!REQUIRED_TLS.matcher(query.toLowerCase(Locale.ROOT)).find()) {
                throw new SQLException(
                        "DB_URL must include sslMode=REQUIRED to protect the database connection.");
            }

            PoolProperties properties = new PoolProperties();
            properties.setUrl(normalizedUrl);
            properties.setUsername(username);
            properties.setPassword(password);
            properties.setDriverClassName("com.mysql.cj.jdbc.Driver");
            properties.setInitialSize(0);
            properties.setMaxActive(8);
            properties.setMaxIdle(4);
            properties.setMinIdle(0);
            properties.setMaxWait(8000);
            properties.setTestOnBorrow(false);
            properties.setTestWhileIdle(true);
            properties.setValidationQuery("SELECT 1");
            properties.setValidationInterval(30000);
            properties.setTimeBetweenEvictionRunsMillis(60000);
            properties.setMinEvictableIdleTimeMillis(300000);
            properties.setRemoveAbandoned(false);
            properties.setJmxEnabled(false);

            DataSource created = new DataSource();
            created.setPoolProperties(properties);
            pool = created;
            return created;
        }
    }

    /** Releases pooled connections when the web application is stopped or reloaded. */
    public static synchronized void closePool() {
        DataSource current = pool;
        pool = null;
        if (current != null) current.close();
    }

    private static String environmentValue(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
