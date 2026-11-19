import java.nio.file.Files
import groovy.json.JsonOutput
import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.3.21"
    application
}

application {
    mainClass.set("dev.writhelm.service.AutomaticTestClient")
    applicationName = "writhelm-test-client"
}

// The canonical codecs are the current wire-format authority, not a handwritten approximation
// advertised as a complete JSON Schema. Ship exact public contract sources with content digests.
val clientContractGroups = mapOf(
    "work-command3" to listOf("work/WorkCodec.kt", "work/WorkValues.kt", "work/WorkValueRegistry.kt", "work/WorkValueDecoders.kt", "work/WorkValueEncoders.kt", "codec/WorkProtocol.kt", "protocol/model-work-v1.json"),
    "work-execution-values" to listOf("work/ExecutionDescriptors.kt", "work/Model2Values.kt", "work/ResultDescriptors.kt"),
    "knowledge-command1" to listOf("codec/CommandCodec.kt", "codec/ReviewSubmission.kt", "codec/CoreRecordCodec.kt", "codec/CoreSnapshotCodec.kt", "codec/KnowledgeProtocol.kt", "workflow/WorkflowRecords.kt", "workflow/ReviewPacket.kt"),
    "knowledge-read1" to listOf("app/H1LegacyReads.kt", "app/H1ProtectedReads.kt", "app/H1Navigation.kt", "codec/CoreRecordCodec.kt", "knowledge/References.kt"),
    "work-read2" to listOf("work/WorkCodec.kt", "work/WorkValues.kt", "work/WorkValueRegistry.kt", "work/WorkValueDecoders.kt", "work/WorkValueEncoders.kt", "codec/WorkProtocol.kt", "protocol/model-work-v1.json"),
    "wire-json" to listOf("codec/StrictJson.kt", "codec/WorkIdentityCodec.kt"),
)
val clientContractsDirectory = layout.buildDirectory.dir("generated/client-contracts")
val generateClientContracts by tasks.registering {
    val core = layout.projectDirectory.dir("src/main/kotlin/dev/writhelm/core")
    fun contractFile(relative:String)=if(relative=="protocol/model-work-v1.json")
        layout.projectDirectory.file("src/main/resources/writhelm/model-work-v1.json") else core.file(relative)
    inputs.files(clientContractGroups.values.flatten().distinct().map { contractFile(it) })
    outputs.dir(clientContractsDirectory)
    doLast {
        val contracts = clientContractGroups.mapValues { (namespace, paths) ->
            val files = paths.map { relative ->
                val bytes = contractFile(relative).asFile.readBytes()
                mapOf("path" to relative, "sha256" to MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                    "source" to bytes.toString(Charsets.UTF_8))
            }
            JsonOutput.toJson(mapOf("formatVersion" to 1, "kind" to "canonical-codec-source-v1", "namespace" to namespace,
                "notice" to "Exact wire codec sources, not a standalone JSON Schema validator or execution/approval evidence", "files" to files)).also {
                check(it.toByteArray(Charsets.UTF_8).size <= 512 * 1024) { "Client contract exceeds bounded schema tool limit: $namespace" }
            }
        }
        val bytes = JsonOutput.toJson(contracts).toByteArray(Charsets.UTF_8)
        check(bytes.size <= 1536 * 1024) { "Client contract package exceeds bootstrap budget" }
        val output = clientContractsDirectory.get().file("writhelm/client-contracts.json").asFile
        output.parentFile.mkdirs(); output.writeBytes(bytes)
    }
}
sourceSets["main"].resources.srcDir(clientContractsDirectory)
tasks.processResources { dependsOn(generateClientContracts) }

val modelProtocolDirectory=layout.buildDirectory.dir("generated/model-contracts")
val generateModelContracts by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("compileKotlin"))
    classpath=files(sourceSets["main"].output.classesDirs,layout.projectDirectory.dir("src/main/resources"),configurations.runtimeClasspath)
    mainClass.set("dev.writhelm.core.codec.GenerateKnowledgeProtocol")
    val output=modelProtocolDirectory.get().file("writhelm/model-knowledge-v1.json")
    args(output.asFile.absolutePath)
    outputs.file(output)
    inputs.file(layout.projectDirectory.file("src/main/resources/writhelm/model-work-v1.json"))
    outputs.file(modelProtocolDirectory.get().file("writhelm/model-work-export-v1.json"))
}
sourceSets["main"].resources.srcDir(modelProtocolDirectory)
tasks.processResources { dependsOn(generateModelContracts) }

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
    exclude("**/NativeBridgeJourneyTest.class")
    exclude("**/NativeWorkPresentationTest.class")
    exclude("**/NativePrimaryJourneyTest.class")
    exclude("**/NativeWorkerPreparationJourneyTest.class")
}

tasks.withType<Test>().configureEach {
    val nativeJS = providers.environmentVariable("WRITHELM_NATIVE_JS")
    inputs.property("nativeJSConfigured", nativeJS.isPresent)
    if (nativeJS.isPresent) inputs.files(fileTree(nativeJS.get()) { include("**/*.js", "**/*.cjs") })
    // Explicit model-free external adapter journey: changing its compiled driver, adapter or GUI
    // must invalidate a prior pass/skip; a path-only environment variable is not a Gradle input.
    val approvalClient = providers.environmentVariable("WRITHELM_APPROVAL_CLIENT")
    inputs.property("approvalClientConfigured", approvalClient.isPresent)
    if (approvalClient.isPresent) {
        val driver = file(approvalClient.get())
        inputs.file(driver)
        inputs.files(fileTree(driver.parentFile.parentFile.resolve("src")) { include("**/*.js", "**/*.cjs") })
        inputs.file(driver.parentFile.parentFile.parentFile.resolve("dist/gui-experimental.cjs"))
    }
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
        // Disposable Git stores can contain tens of thousands of directories. Keep them out of
        // the source tree so an attached development client's recursive watcher does not exhaust
        // host resources. Use the JVM's platform-selected temp root, not a Unix portable default.
        val selected = providers.environmentVariable(name)
        if (selected.isPresent) environment(name, selected.get())
        else doFirst {
            val fallback = Files.createTempDirectory("writhelm-test-$relative-").toFile()
            environment(name, fallback.absolutePath)
            logger.lifecycle("{} runtime artifacts: {}", name, fallback)
        }
    }
}

tasks.register<Test>("nativeComponentTest") {
    dependsOn(tasks.named("installDist"))
    description = "Runs the explicit model-free JVM/HTTP/Node/DOM component journey, not an installed host."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/NativeControllerJourneyTest.class")
    include("**/NativeBridgeJourneyTest.class")
    include("**/NativeWorkPresentationTest.class")
    include("**/NativePrimaryJourneyTest.class")
    include("**/NativeWorkerPreparationJourneyTest.class")
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "128m"
    val launcherName = if (System.getProperty("os.name").lowercase().startsWith("windows")) "writhelm-test-client.bat" else "writhelm-test-client"
    environment("WRITHELM_CLIENT_LAUNCHER", layout.buildDirectory.file("install/writhelm-test-client/bin/$launcherName").get().asFile.absolutePath)
    jvmArgs("-XX:ActiveProcessorCount=2")
    doFirst {
        check(providers.environmentVariable("WRITHELM_NODE").isPresent &&
            providers.environmentVariable("WRITHELM_NATIVE_ADAPTER").isPresent) {
            "nativeComponentTest requires explicit WRITHELM_NODE and WRITHELM_NATIVE_ADAPTER; see the H1 fixture README"
        }
    }
}
