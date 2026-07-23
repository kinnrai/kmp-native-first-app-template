import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    
    android {
       namespace = "com.example.kmpnativefirst.app.sharedUI"
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
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            api(project(":app:sharedLogic"))
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTest)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
