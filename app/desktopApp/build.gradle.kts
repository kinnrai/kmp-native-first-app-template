import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

abstract class VerifyDistributableRuntimeModules : DefaultTask() {
    @get:InputDirectory
    abstract val appImageDirectory: DirectoryProperty

    @get:Input
    abstract val requiredModules: ListProperty<String>

    @TaskAction
    fun verifyModules() {
        val releaseFile = appImageDirectory.get().asFile
            .walkTopDown()
            .firstOrNull { file ->
                file.isFile &&
                    file.name == "release" &&
                    file.readText().contains("MODULES=")
            }
            ?: error("The packaged Java runtime release file was not found.")
        val packagedModules = Regex("""MODULES="([^"]+)"""")
            .find(releaseFile.readText())
            ?.groupValues
            ?.get(1)
            ?.split(' ')
            ?.toSet()
            .orEmpty()
        val missingModules = requiredModules.get().toSet() - packagedModules

        check(missingModules.isEmpty()) {
            "The packaged Java runtime is missing: ${missingModules.sorted()}."
        }
    }
}

val requiredRuntimeModules = setOf(
    "java.instrument",
    "java.management",
    "java.sql",
    "jdk.unsupported",
)

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:sharedLogic"))
    implementation(project(":app:sharedUI"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
    application {
        mainClass = "com.example.kmpnativefirst.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            modules(*requiredRuntimeModules.toTypedArray())
            packageName = "com.example.kmpnativefirst"
            packageVersion = "1.0.0"
        }
    }
}

val distributableDirectory = layout.buildDirectory.dir(
    "compose/binaries/main/app",
)
val verifyDistributableRuntimeModules by tasks.registering(
    VerifyDistributableRuntimeModules::class,
) {
    group = "verification"
    description = "Verifies that the packaged runtime contains required JDK modules."
    dependsOn(tasks.named("createDistributable"))
    appImageDirectory.set(distributableDirectory)
    requiredModules.set(requiredRuntimeModules.sorted())
}

tasks.named("check") {
    dependsOn(verifyDistributableRuntimeModules)
}
