package ai.flowguard.oracles.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

public final class PostgresOracleTestSupport {

    public static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private PostgresOracleTestSupport() {
    }

    public static JdbcTemplate jdbc(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        String jdbcUrl = postgres.getJdbcUrl();
        ds.setUrl(jdbcUrl.contains("?") ? jdbcUrl + "&currentSchema=fixture" : jdbcUrl + "?currentSchema=fixture");
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        applySchema(jdbc);
        return jdbc;
    }

    public static void applySchema(JdbcTemplate jdbc) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/wallet_schema.sql"));
        populator.execute(jdbc.getDataSource());
    }

    public static void seedCustomerAccount(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO fixture.accounts (id, currency, opening_balance_minor, current_balance_minor, overdraft_allowed, row_version)
                VALUES (?, 'USD', 0, 0, false, 0)
                ON CONFLICT (id) DO NOTHING
                """, CUSTOMER);
    }
}
