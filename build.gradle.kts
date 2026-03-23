plugins {
    java
    id("org.springframework.boot") version "${property("springBootVersion")}"
    id("io.spring.dependency-management") version "${property("springDependencyManagementVersion")}"
}

val javaVersion: String by project
val jjwtVersion: String by project
val tikaVersion: String by project
val minioVersion: String by project
val pgvectorVersion: String by project
val springdocVersion: String by project
val testcontainersVersion: String by project

group = "${property("group")}"
version = "${property("version")}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Database ---
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.pgvector:pgvector:$pgvectorVersion")

    // --- MinIO (S3-compatible object storage) ---
    implementation("io.minio:minio:$minioVersion")

    // --- Apache Tika (document parsing) ---
    implementation("org.apache.tika:tika-core:$tikaVersion")
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")

    // --- Claude API SDK ---
    implementation("com.anthropic:anthropic-java:1.5.0")

    // --- JWT ---
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // --- API Documentation ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // --- Monitoring ---
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
