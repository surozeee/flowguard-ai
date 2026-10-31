package ai.flowguard.fixture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class DatasourceUrlGuard implements ApplicationRunner {

    private final String datasourceUrl;

    public DatasourceUrlGuard(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException("DATASOURCE_URL is required");
        }
        if (!datasourceUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("DATASOURCE_URL must be a jdbc:postgresql URL");
        }
        String lower = datasourceUrl.toLowerCase(Locale.ROOT);
        if (!lower.contains("currentschema=fixture")) {
            throw new IllegalStateException("DATASOURCE_URL must include currentSchema=fixture");
        }
        if (runningInContainer() && loopbackHost(datasourceUrl)) {
            throw new IllegalStateException(
                    "DATASOURCE_URL must not use localhost inside Docker; "
                            + "set DATASOURCE_URL=jdbc:postgresql://postgres:5432/flowguard?currentSchema=fixture");
        }
    }

    private static boolean runningInContainer() {
        return Files.exists(Path.of("/.dockerenv"));
    }

    private static boolean loopbackHost(String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h);
        } catch (Exception e) {
            return false;
        }
    }
}
