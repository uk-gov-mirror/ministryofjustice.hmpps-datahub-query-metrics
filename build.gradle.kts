plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.7"
  kotlin("plugin.spring") version "2.4.10"
}

val awsVersion = "1.8.44"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
  implementation("aws.sdk.kotlin:athena:$awsVersion")
  implementation("aws.sdk.kotlin:redshiftdata:$awsVersion")
  implementation("aws.sdk.kotlin:sts:$awsVersion")
  implementation("io.prometheus:prometheus-metrics-core")
  implementation("io.prometheus:prometheus-metrics-exporter-httpserver")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.1")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
