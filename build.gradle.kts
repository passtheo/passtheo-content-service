plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("checkstyle")
    id("jacoco")
}

group = "com.passtheo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-core:2.17.2")
            force("com.fasterxml.jackson.core:jackson-databind:2.17.2")
            force("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
            force("com.fasterxml.jackson:jackson-bom:2.17.2")
            exclude(group = "tools.jackson")
            exclude(group = "io.hypersistence")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // PassTheo shared libraries
    implementation("com.passtheo:shared-core:1.0.0-SNAPSHOT")
    implementation("com.passtheo:shared-security:1.0.0-SNAPSHOT")
    implementation("com.passtheo:shared-events:1.0.0-SNAPSHOT") {
        exclude(group = "org.lz4", module = "lz4-java")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
    }
    testImplementation("com.passtheo:shared-testing:1.0.0-SNAPSHOT") {
        exclude(group = "org.lz4", module = "lz4-java")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
    }

    // LZ4 dependency - use single version
    implementation("org.lz4:lz4-java:1.8.0")

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-reactor-netty")
    }
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.flywaydb:flyway-core:12.1.0") {
        exclude(group = "tools.jackson")
        exclude(group = "com.fasterxml.jackson.core")
    }
    implementation("org.flywaydb:flyway-database-postgresql:12.1.0") {
        exclude(group = "tools.jackson")
        exclude(group = "com.fasterxml.jackson.core")
    }

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.3.0")

    // Observability
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Jakarta Annotations
    implementation("jakarta.annotation:jakarta.annotation-api")

    // Test dependencies
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("com.intuit.karate:karate-junit5:1.4.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Xlint:-processing")
}

checkstyle {
    toolVersion = "10.12.5"
    configFile = file("${rootProject.projectDir}/config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.bootJar {
    archiveFileName.set("passtheo-content-service.jar")
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.11"
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }

    classDirectories.setFrom(
        files(classDirectories.files.map { file ->
            fileTree(file) {
                exclude("**/dto/**")
                exclude("**/entity/**")
                exclude("**/config/**")
                exclude("**/exception/**")
                exclude("**/*Application.class")
                exclude("**/generated/**")
                exclude("**/mapper/**")
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }

        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
            excludes = listOf(
                "**/dto/**",
                "**/entity/**",
                "**/config/**",
                "**/exception/**",
                "**/*Application.class",
                "**/generated/**",
                "**/mapper/**"
            )
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
