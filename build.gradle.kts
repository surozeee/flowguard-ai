plugins {
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "ai.flowguard"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

tasks.register("day1Integration") {
    group = "verification"
    description = "Run Day 1 full integration tests (Docker required)"
    dependsOn(":integration:day1Integration")
}

tasks.register("day2Verification") {
    group = "verification"
    description = "Run Day 2 oracle tests and 20/20 idempotency race gate (Docker required)"
    dependsOn(":trusted-tests:test", ":fixture-wallet:day2Verification")
}

tasks.register("day5Verification") {
    group = "verification"
    description = "Run Day 5 debit race gate (Docker required)"
    dependsOn(":fixture-wallet:day5Verification")
}

tasks.register("e2eProduction") {
    group = "verification"
    description = "Production E2E integration gate (Docker required)"
    dependsOn(":integration:e2eProduction")
}

tasks.register("e2eEnterprise") {
    group = "verification"
    description = "Enterprise inbox/outbox E2E (Docker required)"
    dependsOn(":integration:e2eEnterprise")
}

tasks.register("e2eAuth") {
    group = "verification"
    description = "Auth + secured gateway E2E (Docker required)"
    dependsOn(":integration:e2eAuth")
}

tasks.register("e2eAll") {
    group = "verification"
    description = "All E2E integration suites (Docker required)"
    dependsOn(":integration:e2eAll")
}

tasks.register("milestoneVerification") {
    group = "verification"
    description = "Run Day 1-5 automated gates (Docker required)"
    dependsOn(
        "day1Integration",
        "day2Verification",
        ":benchmarks:baseline:baselineFreeze",
        "day5Verification",
        ":integration:test",
        "e2eAll"
    )
}

tasks.register("day3Verification") {
    group = "verification"
    description = "Run Day 3 frozen baseline benchmark (Docker required)"
    dependsOn(":benchmarks:baseline:baselineFreeze")
}
