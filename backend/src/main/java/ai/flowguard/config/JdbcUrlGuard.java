package ai.flowguard.config;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

final class JdbcUrlGuard {

    private JdbcUrlGuard() {}

    static String requirePostgres(String url, String envName) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(envName + " is required (jdbc:postgresql://host:port/db?currentSchema=...)");
        }
        String trimmed = url.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(envName + " must be a jdbc:postgresql URL");
        }
        return trimmed;
    }

    static String resolveFixtureUrl(String datasourceUrl, String springDatasourceUrl) {
        if (datasourceUrl != null && !datasourceUrl.isBlank()) {
            String url = requirePostgres(datasourceUrl, "DATASOURCE_URL");
            rejectSplitBrain(url, springDatasourceUrl);
            requireSchemaHint(url, "fixture");
            return url;
        }
        if (springDatasourceUrl != null && !springDatasourceUrl.isBlank()) {
            String derived = springDatasourceUrl.replace("currentSchema=metadata", "currentSchema=fixture");
            return requirePostgres(derived, "DATASOURCE_URL");
        }
        throw new IllegalStateException("DATASOURCE_URL is required for the fixture ledger");
    }

    static void requireSchemaHint(String url, String schema) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.contains("currentschema=" + schema.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("DATASOURCE_URL must target schema '" + schema + "' (currentSchema=" + schema + ")");
        }
    }

    static void rejectSplitBrain(String datasourceUrl, String springDatasourceUrl) {
        if (springDatasourceUrl == null || springDatasourceUrl.isBlank()) {
            return;
        }
        String fixtureHost = host(datasourceUrl);
        String metadataHost = host(springDatasourceUrl);
        if (fixtureHost == null || metadataHost == null) {
            return;
        }
        if (isLoopback(fixtureHost) && !isLoopback(metadataHost)) {
            throw new IllegalStateException(
                    "DATASOURCE_URL uses localhost while SPRING_DATASOURCE_URL uses '"
                            + metadataHost
                            + "'. In Docker set DATASOURCE_URL=jdbc:postgresql://postgres:5432/flowguard?currentSchema=fixture");
        }
    }

    static String host(String jdbcUrl) {
        try {
            String stripped = jdbcUrl.substring("jdbc:".length());
            URI uri = URI.create(stripped);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return Objects.equals(h, "localhost") || h.equals("127.0.0.1") || h.equals("::1");
    }
}
