plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.example.kmpnativefirst"
version = "1.0.0"
application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverConfigYaml)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
