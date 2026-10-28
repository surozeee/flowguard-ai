package ai.flowguard.meta;

import ai.flowguard.config.FlowGuardProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaModelClientTest {

    @Test
    void defaultPlanForCreditObjective() {
        InvestigationPlan plan = MetaModelClient.defaultPlan("duplicate credit race");
        assertThat(plan.experimentType()).isEqualTo("CONCURRENT_DUPLICATE");
        assertThat(plan.invariantId()).isEqualTo("PAYMENT_EFFECT_ONCE");
    }

    @Test
    void defaultPlanHonorsPreferredExperimentType() {
        InvestigationPlan plan = MetaModelClient.defaultPlan("duplicate credit race", 2, "CONCURRENT_DEBIT");
        assertThat(plan.experimentType()).isEqualTo("CONCURRENT_DEBIT");
    }

    @Test
    void stubWhenNoKeyAndStubEnabled() {
        FlowGuardProperties properties = propertiesWithMeta("", true);
        MetaModelClient client = new MetaModelClient(properties, new ObjectMapper());
        MetaProposal proposal = client.proposeDetailed(ProposeRequest.of("test objective", 3));
        assertThat(proposal.source()).isEqualTo(MetaProposal.STUB);
        assertThat(proposal.plan().experimentType()).isEqualTo("CONCURRENT_DUPLICATE");
    }

    @Test
    void extractJsonStripsMarkdownFence() {
        String raw = """
                ```json
                {"experimentType":"CONCURRENT_DUPLICATE","invariantId":"PAYMENT_EFFECT_ONCE"}
                ```
                """;
        assertThat(MetaModelClient.extractJson(raw)).contains("CONCURRENT_DUPLICATE");
    }

    @Test
    void completeJsonReturnsStubPayloadWhenNoKey() {
        FlowGuardProperties properties = propertiesWithMeta("", true);
        MetaModelClient client = new MetaModelClient(properties, new ObjectMapper());
        MetaJsonResult result = client.completeJson(
                "system",
                "user",
                Map.of("headline", "stub-headline"));
        assertThat(result.source()).isEqualTo(MetaProposal.STUB);
        assertThat(result.json().path("headline").asText()).isEqualTo("stub-headline");
    }

    @Test
    void sanitizeMapsUnknownTypeUsingObjective() {
        InvestigationPlan raw = new InvestigationPlan(
                "PAYMENT_EFFECT_ONCE",
                "h",
                "LONG_MECHANISM_NAME_FROM_MODEL",
                "duplicate-credit-race",
                Map.of("parallelRequests", 3),
                "DUPLICATE_CREDIT",
                "because");
        InvestigationPlan sanitized = MetaModelClient.sanitize(raw, "duplicate credit", 2);
        assertThat(sanitized.experimentType()).isEqualTo("CONCURRENT_DUPLICATE");
        assertThat(sanitized.parameters()).containsKey("checkpoint");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MODEL_API_KEY", matches = ".+")
    void liveMetaApiReturnsInvestigationPlanMultipleTimes() {
        String baseUrl = System.getenv().getOrDefault("MODEL_API_BASE_URL", "https://api.meta.ai/v1");
        String modelId = System.getenv().getOrDefault("MODEL_ID", "muse-spark-1.3");
        String apiKey = System.getenv("MODEL_API_KEY");
        FlowGuardProperties properties = propertiesWithMeta(baseUrl, apiKey, modelId, false);
        MetaModelClient client = new MetaModelClient(properties, new ObjectMapper());
        String[] objectives = {
                "Detect duplicate credit under concurrent idempotency",
                "Investigate at-most-once processing and duplicate credit risk",
                "Find saga choreography gaps after a settled transfer"
        };
        for (int i = 0; i < objectives.length; i++) {
            MetaProposal proposal = client.proposeDetailed(ProposeRequest.of(objectives[i], 3 - i));
            InvestigationPlan plan = proposal.plan();
            assertThat(proposal.source()).as("call %d source", i + 1).isEqualTo(MetaProposal.LIVE);
            assertThat(plan.invariantId()).as("call %d invariant", i + 1).isNotBlank();
            assertThat(MetaModelClient.ALLOWED_TYPES).contains(plan.experimentType());
            assertThat(plan.hypothesis()).as("call %d hypothesis", i + 1).isNotBlank();
        }
    }

    private static FlowGuardProperties propertiesWithMeta(String apiKey, boolean stubWhenNoKey) {
        return propertiesWithMeta("https://api.meta.ai/v1", apiKey, "muse-spark-1.3", stubWhenNoKey);
    }

    private static FlowGuardProperties propertiesWithMeta(
            String baseUrl, String apiKey, String modelId, boolean stubWhenNoKey) {
        return new FlowGuardProperties(
                null,
                new FlowGuardProperties.Meta(baseUrl, apiKey, modelId, stubWhenNoKey),
                null,
                null,
                null);
    }
}
