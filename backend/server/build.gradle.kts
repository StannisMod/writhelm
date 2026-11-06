plugins {
    kotlin("jvm") version "2.3.21"
}

group = "dev.writhelm"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.7")
    implementation("io.ktor:ktor-server-cio-jvm:3.4.3")
    implementation("io.modelcontextprotocol:kotlin-sdk-server-jvm:0.13.0")
    testImplementation("io.ktor:ktor-client-cio-jvm:3.4.3")
    testImplementation("io.modelcontextprotocol:kotlin-sdk-client-jvm:0.13.0")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "128m"
    jvmArgs("-XX:ActiveProcessorCount=2")
    // Native Node/DOM components have explicit prerequisites and a separate task, not a hidden
    // dependency on a developer's temporary build directory in the portable test selection.
    exclude("**/NativeControllerJourneyTest.class")
}

tasks.withType<Test>().configureEach {
    val fixtures = layout.projectDirectory.dir("src/test/resources/h1")
    mapOf(
        "WRITHELM_H1_READY_ROOT" to "frozen",
        "WRITHELM_H1_WORK_READY_ROOT" to "work",
        "WRITHELM_H1_AUTH_READY_ROOT" to "auth",
        "WRITHELM_H1_BODY_PROBE_ROOT" to "body",
        "WRITHELM_COORDINATION_READY_ROOT" to "composed",
    ).forEach { (name, relative) ->
        environment(name, providers.environmentVariable(name).getOrElse(fixtures.dir(relative).asFile.absolutePath))
    }
    mapOf(
        "WRITHELM_STORE_TEST_ROOT" to "store",
        "WRITHELM_APP_TEST_ROOT" to "app",
        "API_EVIDENCE_ROOT" to "evidence",
    ).forEach { (name, relative) ->
        val fallback = layout.buildDirectory.dir("test-runtime/$relative").get().asFile
        doFirst { if (!providers.environmentVariable(name).isPresent) fallback.mkdirs() }
        environment(name, providers.environmentVariable(name).getOrElse(fallback.absolutePath))
    }
}

tasks.register<Test>("nativeComponentTest") {
    description = "Runs the explicit model-free JVM/HTTP/Node/DOM component journey, not an installed host."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/NativeControllerJourneyTest.class")
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "128m"
    jvmArgs("-XX:ActiveProcessorCount=2")
    doFirst {
        check(providers.environmentVariable("WRITHELM_NODE").isPresent &&
            providers.environmentVariable("WRITHELM_NATIVE_ADAPTER").isPresent) {
            "nativeComponentTest requires explicit WRITHELM_NODE and WRITHELM_NATIVE_ADAPTER; see the H1 fixture README"
        }
    }
}
