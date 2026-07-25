@file:OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)

import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64()
    ).forEach { appleTarget ->
        appleTarget.binaries.configureEach {
            if (appleTarget.name == "macosArm64") {
                disableNativeCache(
                    version = DisableCacheInKotlinVersion.`2_4_10`,
                    reason = "SQLiter 1.3.3 references optional SQLite APIs unavailable in the macOS SDK",
                    issueUrl = URI("https://github.com/sqldelight/sqldelight/issues/5305"),
                )
            }
        }
        appleTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
            export(project(":core"))
        }
    }
    
    jvm()
    
    js {
        outputModuleName = "sharedLogic"
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target = "es2015"
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
        compilations["main"].packageJson {
            name = "shared-logic"
            customField("private", true)
        }
    }
    
    android {
       namespace = "com.example.kmpnativefirst.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.serializationKotlinxJsonMultiplatform)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.clientMock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientOkHttp)
            implementation(libs.sqldelight.androidDriver)
        }
        appleMain.dependencies {
            implementation(libs.ktor.clientDarwin)
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.sqldelight.sqliteDriver)
        }
        jsMain.dependencies {
            implementation(libs.ktor.clientJs)
            implementation(libs.wrappers.browser)
        }
    }
}

sqldelight {
    databases {
        create("TaskDatabase") {
            packageName.set("com.example.kmpnativefirst.task.data.local.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
