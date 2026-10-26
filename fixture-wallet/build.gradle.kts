plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":trusted-tests"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

tasks.register<Test>("day5Verification") {
    group = "verification"
    description = "Day 5 concurrent debit race gate (20/20)"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.fixture.verification.Day5*")
    }
}

tasks.register<Test>("day2Verification") {
    group = "verification"
    description = "Day 2 oracle + 20/20 idempotency race gate"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.fixture.verification.Day2*")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("fixture-wallet")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
