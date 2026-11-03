plugins {
    java
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    testImplementation(project(":fixture-wallet"))
    testImplementation(project(":trusted-tests"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.assertj:assertj-core")
}

sourceSets {
    test {
        resources.srcDir(project(":fixture-wallet").file("src/test/resources"))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("baselineFreeze") {
    group = "verification"
    description = "Day 3 frozen baseline idempotency benchmark"
    useJUnitPlatform()
    filter {
        includeTestsMatching("ai.flowguard.benchmarks.baseline.*")
    }
}
