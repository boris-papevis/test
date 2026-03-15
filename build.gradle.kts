plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("io.ktor.plugin") version "3.1.1"
}

group = "com.tequipy"
version = "1.0.0"

application {
    mainClass.set("com.tequipy.weather.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(17)
}
