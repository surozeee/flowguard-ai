package ai.flowguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowguard")
public record FlowGuardProperties(
        Fixture fixture,
        Meta meta,
        KafkaTopics kafka,
        Security security,
        Gateway gateway
) {
    public FlowGuardProperties {
        kafka = normalizeKafka(kafka);
        gateway = gateway == null ? new Gateway("", "") : gateway;
        security = security == null ? new Security("", new Security.OAuth(false, "", "", "")) : security;
        meta = meta == null ? new Meta("", "", "muse-spark-1.3", true) : meta;
        fixture = fixture == null ? new Fixture("", "", "") : fixture;
    }

    private static KafkaTopics normalizeKafka(KafkaTopics kafka) {
        KafkaTopics.TopicNames defaults = defaultTopicNames();
        KafkaTopics.ConsumerGroups defaultGroups = new KafkaTopics.ConsumerGroups(null, null, null);
        if (kafka == null) {
            return new KafkaTopics(Boolean.TRUE, defaults, defaultGroups);
        }
        boolean messagingEnabled = kafka.messagingEnabled();
        KafkaTopics.TopicNames topics = kafka.topics();
        KafkaTopics.ConsumerGroups groups = kafka.groups() == null ? defaultGroups : kafka.groups();
        if (topics == null) {
            return new KafkaTopics(messagingEnabled, defaults, groups);
        }
        return new KafkaTopics(
                messagingEnabled,
                new KafkaTopics.TopicNames(
                        orDefault(topics.investigations(), defaults.investigations()),
                        orDefault(topics.experiments(), defaults.experiments()),
                        orDefault(topics.evidence(), defaults.evidence()),
                        orDefault(topics.evidenceProcessed(), defaults.evidenceProcessed()),
                        orDefault(topics.transactions(), defaults.transactions()),
                        orDefault(topics.sagaEvents(), defaults.sagaEvents())),
                groups);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static KafkaTopics.TopicNames defaultTopicNames() {
        return new KafkaTopics.TopicNames(
                "flowguard.investigations",
                "flowguard.experiments",
                "flowguard.evidence",
                "flowguard.evidence.processed",
                "flowguard.transactions",
                "flowguard.saga.events");
    }

    public record Gateway(String baseUrl, String internalToken) {
        public boolean enabled() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }

    public record Security(String apiKey, OAuth oauth) {
        public boolean apiKeyEnabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean requiresAuthentication() {
            return apiKeyEnabled() || (oauth != null && oauth.enabled());
        }

        public record OAuth(
                boolean enabled,
                String introspectionUri,
                String clientId,
                String clientSecret
        ) {
        }
    }

    public record Fixture(String baseUrl, String customerAccountId, String internalToken) {
    }

    public record Meta(String baseUrl, String apiKey, String modelId, boolean stubWhenNoKey) {
    }

    public record KafkaTopics(Boolean messagingEnabled, TopicNames topics, ConsumerGroups groups) {
        public KafkaTopics {
            if (messagingEnabled == null) {
                messagingEnabled = true;
            }
        }

        public record TopicNames(
                String investigations,
                String experiments,
                String evidence,
                String evidenceProcessed,
                String transactions,
                String sagaEvents
        ) {
        }

        public record ConsumerGroups(String transactions, String sagaEvents, String evidence) {
            public ConsumerGroups {
                if (transactions == null || transactions.isBlank()) {
                    transactions = "flowguard-backend-transactions";
                }
                if (sagaEvents == null || sagaEvents.isBlank()) {
                    sagaEvents = "flowguard-backend-saga-choreography";
                }
                if (evidence == null || evidence.isBlank()) {
                    evidence = "flowguard-backend-evidence";
                }
            }
        }
    }
}
