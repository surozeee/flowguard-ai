package ai.flowguard.fixture.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Day2ReplayEvidenceWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Day2ReplayEvidenceWriter() {
    }

    static void write(List<Map<String, Object>> runs, int violations, int required) {
        try {
            Path dir = Path.of("build", "reports", "day2");
            Files.createDirectories(dir);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("gate", "DAY2_IDEMPOTENCY_RACE");
            report.put("oracleVersion", ai.flowguard.oracles.FinancialOracle.VERSION);
            report.put("requiredViolations", required);
            report.put("observedViolations", violations);
            report.put("passed", violations == required);
            report.put("recordedAt", Instant.now().toString());
            report.put("runs", runs);
            Files.writeString(dir.resolve("idempotency-replay.json"), MAPPER.writeValueAsString(report));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write Day 2 replay evidence", e);
        }
    }
}
