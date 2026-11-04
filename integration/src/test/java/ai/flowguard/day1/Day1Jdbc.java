package ai.flowguard.day1;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

final class Day1Jdbc {

    private static JdbcTemplate jdbcTemplate;

    private Day1Jdbc() {
    }

    static JdbcTemplate get() {
        if (jdbcTemplate == null) {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(Day1Infrastructure.POSTGRES.getJdbcUrl());
            dataSource.setUsername(Day1Infrastructure.POSTGRES.getUsername());
            dataSource.setPassword(Day1Infrastructure.POSTGRES.getPassword());
            dataSource.setMaximumPoolSize(2);
            jdbcTemplate = new JdbcTemplate(dataSource);
        }
        return jdbcTemplate;
    }
}
