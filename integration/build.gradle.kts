plugins {
    java
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    testImplementation(project(":backend"))
    testImplementation(project(":api-gateway"))
    testImplementation(project(":auth-server"))
    testImplementation(project(":fixture-wallet"))
    testImplementation(project(":trusted-tests"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.apache.kafka:kafka-clients")
    testImplementation("org.springframework.kafka:spring-kafka")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Local .env often sets SPRING_DATASOURCE_* to host Postgres; integration uses Testcontainers.
    doFirst {
        listOf(
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "MODEL_API_KEY",
            "KAFKA_BOOTSTRAP_SERVERS",
            "SPRING_DATA_REDIS_HOST",
            "SPRING_DATA_REDIS_PORT"
        ).forEach { environment.remove(it) }
    }
}

tasks.register<Test>("day1Integration") {
    group = "verification"
    description = "LedgerGuard Day 1 full stack integration gate"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.day1.*")
    }
}

tasks.register<Test>("e2eProduction") {
    group = "verification"
    description = "Production-hardening E2E (Postgres, Redis, Kafka, idempotency, investigation)"
    useJUnitPlatform()
    maxParallelForks = 1
    forkEvery = 0
    jvmArgs("-Xmx1536m", "-XX:MaxMetaspaceSize=384m")
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    filter {
        includeTestsMatching("ai.flowguard.e2e.ProductionStackIntegrationTest")
        includeTestsMatching("ai.flowguard.e2e.OpsStackIntegrationTest")
    }
}

tasks.register<Test>("e2eEnterprise") {
    group = "verification"
    description = "Enterprise inbox/outbox E2E (gateway + fixture + backend)"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.e2e.EnterpriseInboxOutboxE2ETest")
    }
}

tasks.register<Test>("e2eAuth") {
    group = "verification"
    description = "Auth-server + secured API gateway + backend E2E"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.e2e.AuthGatewayStackIntegrationTest")
    }
}

tasks.register<Test>("e2eAll") {
    group = "verification"
    description = "All end-to-end integration tests (Docker required)"
    useJUnitPlatform()
    maxParallelForks = 1
    forkEvery = 0
    jvmArgs("-Xmx768m")
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    filter {
        includeTestsMatching("ai.flowguard.e2e.*")
    }
}
