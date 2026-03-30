package com.passtheo.content.config;

import com.passtheo.shared.core.context.TenantContext;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DataSource configuration that sets the PostgreSQL session variable
 * for RLS tenant isolation on every connection checkout.
 */
@Configuration
public class DataSourceConfig {

    /**
     * Creates the actual HikariCP data source.
     *
     * @param url      JDBC URL
     * @param username database username
     * @param password database password
     * @return the raw DataSource
     */
    @Bean
    public DataSource actualDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    /**
     * Creates the primary DataSource wrapped with tenant-aware connection setup.
     *
     * @param actualDataSource the underlying HikariCP data source
     * @return tenant-aware DataSource
     */
    @Bean
    @Primary
    public DataSource dataSource(
            DataSource actualDataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate) {
        Flyway.configure()
                .dataSource(actualDataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .load()
                .migrate();
        return new LazyConnectionDataSourceProxy(new TenantAwareDs(actualDataSource));
    }


    /**
     * Wraps a DataSource to set app.tenant_id on each connection for RLS.
     */
    private static class TenantAwareDs implements DataSource {

        private final DataSource delegate;

        TenantAwareDs(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = delegate.getConnection();
            setTenantOnConnection(connection);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = delegate.getConnection(username, password);
            setTenantOnConnection(connection);
            return connection;
        }

        private void setTenantOnConnection(Connection connection) throws SQLException {
            if (TenantContext.isSet()) {
                try (var stmt = connection.createStatement()) {
                    stmt.execute("SET app.tenant_id = '" + TenantContext.get() + "'");
                }
            }
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(TenantAwareDs.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
